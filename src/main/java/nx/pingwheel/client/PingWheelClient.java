package nx.pingwheel.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.networking.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.networking.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import nx.pingwheel.client.config.ConfigHandler;
import nx.pingwheel.shared.network.PingLocationPacketS2C;
import nx.pingwheel.shared.network.UpdateChannelPacketC2S;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

import static nx.pingwheel.shared.PingWheel.MOD_ID;

public class PingWheelClient {

	public static final MinecraftClient Game = MinecraftClient.getInstance();
	public static final ConfigHandler ConfigHandler = new ConfigHandler(MOD_ID + ".json");
	public static final Identifier PING_SOUND_ID = new Identifier(MOD_ID, "ping");
	public static final SoundEvent PING_SOUND_EVENT = SoundEvent.of(PING_SOUND_ID);
	public static final Identifier PING_TEXTURE_ID = new Identifier(MOD_ID, "textures/ping.png");

	private static final DeferredRegister<SoundEvent> SOUND_EVENT_DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

	private static final RegistryObject<SoundEvent> PING = SOUND_EVENT_DEFERRED_REGISTER.register("ping", () -> PING_SOUND_EVENT);

	public PingWheelClient() {
		ConfigHandler.load();
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		bus.addListener((RegisterKeyMappingsEvent event) -> {
			event.register(kbPing);
			event.register(kbSettings);
		});
		SOUND_EVENT_DEFERRED_REGISTER.register(bus);
		bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener((helper, resourceManager, loadProfiler, applyProfiler, loadExecutor, applyExecutor) -> reloadTextures(helper, resourceManager, loadExecutor, applyExecutor)));
		MinecraftForge.EVENT_BUS.register(this);
		ClientPlayNetworking.registerGlobalReceiver(PingLocationPacketS2C.ID, (a, b, packet, c) -> ClientCore.onPingLocation(packet));
		ClientPlayConnectionEvents.JOIN.register((a, b, c) -> new UpdateChannelPacketC2S(ConfigHandler.getConfig().getChannel()).send());
	}

	@SubscribeEvent
	public void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_WEATHER)) {
			ClientCore.onRenderWorld(event.getPoseStack(), event.getProjectionMatrix(), event.getPartialTick());
		}
	}

	@SubscribeEvent
	public void onPostGuiRender(RenderGuiEvent.@NotNull Post event) {
		ClientCore.onRenderGUI(event.getGuiGraphics(), event.getPartialTick());
	}

	private CompletableFuture<Void> reloadTextures(ResourceReloader.Synchronizer helper, ResourceManager resourceManager, Executor loadExecutor, Executor applyExecutor) {
		return CompletableFuture
			.supplyAsync(() -> {
				final var canLoadTexture = resourceManager.getResource(PING_TEXTURE_ID).isPresent();

				if (!canLoadTexture) {
					// force texture manager to remove the entry from its index
					Game.getTextureManager().registerTexture(PING_TEXTURE_ID, MissingSprite.getMissingSpriteTexture());
				}

				return canLoadTexture;
			}, loadExecutor)
			.thenCompose(helper::whenPrepared)
			.thenAcceptAsync(canLoadTexture -> {
				if (canLoadTexture) {
					Game.getTextureManager().bindTexture(PING_TEXTURE_ID);
				}
			}, applyExecutor);
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase.equals(TickEvent.Phase.END)) {
			if (kbPing.wasPressed()) {
				ClientCore.markLocation();
			}

			if (kbSettings.wasPressed()) {
				Game.setScreen(new PingWheelSettingsScreen());
			}
		}
	}

	KeyBinding kbPing = new KeyBinding("pingwheel.key.mark-location", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "pingwheel.name");
	KeyBinding kbSettings = new KeyBinding("pingwheel.key.open-settings", InputUtil.Type.KEYSYM, -1, "pingwheel.name");


	@SubscribeEvent
	public void onCommandRegister(RegisterClientCommandsEvent event) {
		UnaryOperator<String> formatChannel = (channel) -> "".equals(channel) ? "§eGlobal §7(default)" : String.format("\"§6%s§r\"", channel);

		Command<ServerCommandSource> helpCallback = (context) -> {
			var output = """
				§f/pingwheel config
				§7(manage pingwheel configuration)
				§f/pingwheel channel
				§7(get your current channel)
				§f/pingwheel channel <channel_name>
				§7(set your current channel, use "" for global channel)""";

			var message = Text.of(output);
			MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
			MinecraftClient.getInstance().getNarratorManager().narrate(message);

			return 1;
		};

		var cmdBase = literal("pingwheel")
				.executes(helpCallback)
				.then(literal("help").executes(helpCallback))
				.then(literal("config")
						.executes((context) -> {
							var client = MinecraftClient.getInstance();
							client.send(() -> client.setScreen(new PingWheelSettingsScreen()));

							return 1;
						}))
				.then(literal("channel")
						.executes((context) -> {
							var currentChannel = ConfigHandler.getConfig().getChannel();
							var message = Text.of(String.format("Current pingwheel channel: %s", formatChannel.apply(currentChannel)));
							MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
							MinecraftClient.getInstance().getNarratorManager().narrate(message);

							return 1;
						})
						.then(argument("channel_name", StringArgumentType.string()).executes((context) -> {
							var newChannel = context.getArgument("channel_name", String.class);

							ConfigHandler.getConfig().setChannel(newChannel);
							ConfigHandler.save();

							var message = Text.of(String.format("Set pingwheel channel to: %s", formatChannel.apply(newChannel)));

							MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
							MinecraftClient.getInstance().getNarratorManager().narrate(message);
							return 1;
						})));
		event.getDispatcher().register(cmdBase);
	}

	@Contract(value = "_ -> new", pure = true)
	public static @NotNull LiteralArgumentBuilder<ServerCommandSource> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	@Contract(value = "_, _ -> new", pure = true)
	public static <T> @NotNull RequiredArgumentBuilder<ServerCommandSource, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}
}

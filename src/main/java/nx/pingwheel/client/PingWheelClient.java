package nx.pingwheel.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
//import lombok.var;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import nx.pingwheel.client.config.ConfigHandler;
import nx.pingwheel.shared.network.PingLocationPacketS2C;
import nx.pingwheel.shared.network.UpdateChannelPacketC2S;
import org.lwjgl.glfw.GLFW;

import java.util.function.UnaryOperator;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static nx.pingwheel.shared.PingWheel.MOD_ID;

public class PingWheelClient {

	public static final MinecraftClient Game = MinecraftClient.getInstance();
	public static final ConfigHandler ConfigHandler = new ConfigHandler(MOD_ID + ".json");
	public static final Identifier PING_TEXTURE_ID = new Identifier(MOD_ID, "textures/ping.png");

	private static final DeferredRegister<SoundEvent> SOUND_EVENT_DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

	public static final RegistryObject<SoundEvent> PING_SOUND;

	static {
		PING_SOUND = SOUND_EVENT_DEFERRED_REGISTER.register("ping", () -> new SoundEvent(new Identifier(MOD_ID, "ping")));
	}

	public static void clientInit() {
		ConfigHandler.load();

		setupKeyBindings();
		setupClientCommands();

		SOUND_EVENT_DEFERRED_REGISTER.register(FMLJavaModLoadingContext.get().getModEventBus());

		HudRenderCallback.EVENT.register(ClientCore::onRenderGUI);
		WorldRenderEvents.END.register(ctx -> ClientCore.onRenderWorld(ctx.matrixStack(), ctx.projectionMatrix(), ctx.tickDelta()));

		ClientPlayNetworking.registerGlobalReceiver(PingLocationPacketS2C.ID, (a, b, packet, c) -> ClientCore.onPingLocation(packet));
		ClientPlayConnectionEvents.JOIN.register((a, b, c) -> new UpdateChannelPacketC2S(ConfigHandler.getConfig().getChannel()).send());

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ReloadListener());
	}

	private static void setupKeyBindings() {
		var kbPing = KeyBindingHelper.registerKeyBinding(new KeyBinding("pingwheel.key.mark-location", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, "pingwheel.name"));
		var kbSettings = KeyBindingHelper.registerKeyBinding(new KeyBinding("pingwheel.key.open-settings", InputUtil.Type.KEYSYM, -1, "pingwheel.name"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (kbPing.wasPressed()) {
				ClientCore.markLocation();
			}

			if (kbSettings.wasPressed()) {
				Game.setScreen(new PingWheelSettingsScreen());
			}
		});
	}

	private static void setupClientCommands() {
		UnaryOperator<String> formatChannel = (channel) -> "".equals(channel) ? "§eGlobal §7(default)" : String.format("\"§6%s§r\"", channel);

		var cmdChannel = literal("channel")
				.executes((context) -> {
					var currentChannel = ConfigHandler.getConfig().getChannel();
					context.getSource().sendFeedback(Text.of(String.format("Current Ping-Wheel channel: %s", formatChannel.apply(currentChannel))));

					return 1;
				})
				.then(argument("channel_name", StringArgumentType.string()).executes((context) -> {
					var newChannel = context.getArgument("channel_name", String.class);

					ConfigHandler.getConfig().setChannel(newChannel);
					ConfigHandler.save();

					context.getSource().sendFeedback(Text.of(String.format("Set Ping-Wheel channel to: %s", formatChannel.apply(newChannel))));

					return 1;
				}));

		var cmdConfig = literal("config")
				.executes((context) -> {
					var client = context.getSource().getClient();
					client.send(() -> client.setScreen(new PingWheelSettingsScreen()));

					return 1;
				});

		Command<FabricClientCommandSource> helpCallback = (context) -> {
			var output = "/pingwheel config\n" +
					"§7(manage pingwheel configuration)\n" +
					"/pingwheel channel\n" +
					"§7(get your current channel)\n" +
					"/pingwheel channel <channel_name>\n" +
					"§7(set your current channel, use \"\" for global channel)";

			context.getSource().sendFeedback(Text.of(output));

			return 1;
		};

		var cmdHelp = literal("help")
				.executes(helpCallback);

		var cmdBase = literal("pingwheel")
				.executes(helpCallback)
				.then(cmdHelp)
				.then(cmdConfig)
				.then(cmdChannel);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(cmdBase));
	}
}

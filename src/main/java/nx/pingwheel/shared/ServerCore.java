package nx.pingwheel.shared;

//import lombok.var;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.networking.api.networking.v1.PacketByteBufs;
import net.fabricmc.networking.api.networking.v1.PlayerLookup;
import net.fabricmc.networking.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nx.pingwheel.shared.network.PingLocationPacketC2S;
import nx.pingwheel.shared.network.PingLocationPacketS2C;
import nx.pingwheel.shared.network.UpdateChannelPacketC2S;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

import static nx.pingwheel.shared.PingWheel.LOGGER;
import static nx.pingwheel.shared.PingWheel.MOD_VERSION;

public class ServerCore {

	private static final Map<UUID, String> playerChannels = new Object2ObjectOpenHashMap<>();

	public static void onPlayerDisconnect(@NotNull ServerPlayerEntity player) {
		playerChannels.remove(player.getUuid());
	}

	public static void onChannelUpdate(ServerPlayerEntity player, PacketByteBuf packet) {
		var channelUpdatePacket = UpdateChannelPacketC2S.parse(packet);

		if (!channelUpdatePacket.isPresent()) {
			LOGGER.warn("invalid channel update from " + String.format("%s (%s)", player.getGameProfile().getName(), player.getUuid()));
			player.sendMessage(Text.of("§c[Ping-Wheel] Channel couldn't be updated. Make sure your version matches the server's version: " + MOD_VERSION), false);
			return;
		}

		updatePlayerChannel(player, channelUpdatePacket.get().getChannel());
	}

	public static void onPingLocation(ServerPlayerEntity player, PacketByteBuf packet) {
		var packetCopy = PacketByteBufs.copy(packet);
		var pingLocationPacket = PingLocationPacketC2S.parse(packet);

		if (!pingLocationPacket.isPresent()) {
			LOGGER.warn("invalid ping location from " + String.format("%s (%s)", player.getGameProfile().getName(), player.getUuid()));
			player.sendMessage(Text.of("§c[Ping-Wheel] Ping couldn't be sent. Make sure your version matches the server's version: " + MOD_VERSION), false);
			return;
		}

		var channel = pingLocationPacket.get().getChannel();

		if (!channel.equals(playerChannels.getOrDefault(player.getUuid(), ""))) {
			updatePlayerChannel(player, channel);
		}

		packetCopy.writeUuid(player.getUuid());

		for (ServerPlayerEntity p : PlayerLookup.world(player.getWorld())) {
			if (!channel.equals(playerChannels.getOrDefault(p.getUuid(), ""))) {
				continue;
			}

			ServerPlayNetworking.send(p, PingLocationPacketS2C.ID, packetCopy);
		}
	}

	private static void updatePlayerChannel(ServerPlayerEntity player, @NotNull String channel) {
		if (channel.isEmpty()) {
			playerChannels.remove(player.getUuid());
			LOGGER.info("Channel update: " + String.format("%s -> Global", player.getGameProfile().getName()));
		} else {
			playerChannels.put(player.getUuid(), channel);
			LOGGER.info("Channel update: " + String.format("%s -> \"%s\"", player.getGameProfile().getName(), channel));
		}
	}
}

package nx.pingwheel.shared;

import net.fabricmc.networking.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.networking.api.networking.v1.ServerPlayNetworking;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import nx.pingwheel.client.PingWheelClient;
import nx.pingwheel.shared.network.PingLocationPacketC2S;
import nx.pingwheel.shared.network.UpdateChannelPacketC2S;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessageFactory;
import org.apache.logging.log4j.message.Message;

@Mod(PingWheel.MOD_ID)
public class PingWheel {

	public static final String MOD_ID = "pingwheel";
	public static final String MOD_VERSION = FMLLoader.getLoadingModList().getModFileById(MOD_ID).versionString();
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID,
		new FormattedMessageFactory() {
			@Override
			public Message newMessage(String message) {
				return super.newMessage("[pingwheel] " + message);
			}
		});

	public PingWheel() {
		if (FMLLoader.getDist().isClient()) new PingWheelClient();
		ServerPlayNetworking.registerGlobalReceiver(PingLocationPacketC2S.ID, (a, player, b, packet, c) -> ServerCore.onPingLocation(player, packet));
		ServerPlayNetworking.registerGlobalReceiver(UpdateChannelPacketC2S.ID, (a, player, b, packet, c) -> ServerCore.onChannelUpdate(player, packet));
		ServerPlayConnectionEvents.DISCONNECT.register((networkHandler, a) -> ServerCore.onPlayerDisconnect(networkHandler.player));
	}
}

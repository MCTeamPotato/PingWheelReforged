package nx.pingwheel.shared.network;

import lombok.var;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import nx.pingwheel.shared.PingWheel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Getter
public class PingLocationPacketC2S {

	private String channel;
	private Vec3d pos;
	@Nullable
	private UUID entity;
	private int sequence;

	public static final Identifier ID = new Identifier(PingWheel.MOD_ID + "-c2s", "ping-location");

	public void send() {
		var packet = PacketByteBufs.create();

		packet.writeString(channel);
		packet.writeDouble(pos.x);
		packet.writeDouble(pos.y);
		packet.writeDouble(pos.z);
		packet.writeBoolean(entity != null);

		if (entity != null) {
			packet.writeUuid(entity);
		}

		packet.writeInt(sequence);

		ClientPlayNetworking.send(ID, packet);
	}

	public static Optional<PingLocationPacketC2S> parse(PacketByteBuf buf) {
		try {
			var channel = buf.readString(128);
			var pos = new Vec3d(
				buf.readDouble(),
				buf.readDouble(),
				buf.readDouble());
			var uuid = buf.readBoolean() ? buf.readUuid() : null;
			var sequence = buf.readInt();

			if (buf.readableBytes() > 0) {
				return Optional.empty();
			}

			return Optional.of(new PingLocationPacketC2S(channel, pos, uuid, sequence));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}

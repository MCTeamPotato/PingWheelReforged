package nx.pingwheel.client;

import lombok.var;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import static nx.pingwheel.client.PingWheelClient.Game;

@OnlyIn(Dist.CLIENT)
public class DirectionalSoundInstance extends MovingSoundInstance {

	private final Vec3d pos;

	public DirectionalSoundInstance(SoundEvent sound,
									   SoundCategory category,
									   float volume,
									   float pitch,
									   Vec3d pos) {
		super(sound, category, Random.create(0));

		this.volume = volume;
		this.pitch = pitch;
		this.pos = pos;
		tick();
	}

	@Override
	public void tick() {
		if (Game.player == null) {
			this.setDone();
			return;
		}

		var playerPos = Game.player.getPos();
		var vecBetween = playerPos.relativize(this.pos);
		var mappedDistance = Math.min(vecBetween.length(), 64.0) / 64.0 * 14.0;
		var soundDirection = vecBetween.normalize().multiply(mappedDistance);
		var soundPos = playerPos.add(soundDirection);

		this.x = soundPos.x;
		this.y = soundPos.y;
		this.z = soundPos.z;
	}
}

package nx.pingwheel.client;

//import lombok.var;
import com.mojang.serialization.Codec;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.OrderableTooltip;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import nx.pingwheel.client.config.Config;

import java.util.Collections;
import java.util.List;

import static nx.pingwheel.client.PingWheelClient.ConfigHandler;

public class PingWheelSettingsScreen extends Screen {

	private final Config config;

	private Screen parent;
	private ButtonListWidget list;
	private TextFieldWidget channelTextField;

	public PingWheelSettingsScreen() {
		super(Text.translatable("pingwheel.settings.title"));
		this.config = ConfigHandler.getConfig();
	}

	public PingWheelSettingsScreen(Screen parent) {
		this();
		this.parent = parent;
	}

	@Override
	public void tick() {
		this.channelTextField.tick();
	}

	@Override
	protected void init() {
		this.list = new ButtonListWidget(this.client, this.width, this.height, 32, this.height - 32, 25);

		final var pingVolumeOption = getPingVolumeOption();
		final var pingDurationOption = getPingDurationOption();
		this.list.addOptionEntry(pingVolumeOption, pingDurationOption);

		final var pingDistanceOption = getPingDistanceOption();
		final var correctionPeriodOption = getCorrectionPeriodOption();
		this.list.addOptionEntry(pingDistanceOption, correctionPeriodOption);

		final var itemIconsVisibleOption = getItemIconsVisibleOption();
		final var pingSizeOption = getPingSizeOption();
		this.list.addOptionEntry(itemIconsVisibleOption, pingSizeOption);

		this.channelTextField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 140, 200, 20, Text.empty());
		this.channelTextField.setMaxLength(128);
		this.channelTextField.setText(config.getChannel());
		this.channelTextField.setChangedListener(config::setChannel);
		this.addSelectableChild(this.channelTextField);

		this.addSelectableChild(this.list);

		this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, this.height - 27, 200, 20, ScreenTexts.DONE, (button) -> close()));
	}

	@Override
	public void close() {
		ConfigHandler.save();

		if (parent != null && this.client != null) {
			this.client.setScreen(parent);
		} else {
			super.close();
		}
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		this.list.render(matrices, mouseX, mouseY, delta);
		drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 20, 16777215);

		drawTextWithShadow(matrices, this.textRenderer, Text.translatable("pingwheel.settings.channel"), this.width / 2 - 100, 128, 10526880);
		this.channelTextField.render(matrices, mouseX, mouseY, delta);

		super.render(matrices, mouseX, mouseY, delta);

		var tooltipLines = getHoveredButtonTooltip(this.list, mouseX, mouseY);

		if (tooltipLines.isEmpty() && (this.channelTextField.isHovered() && !this.channelTextField.isFocused())) {
			tooltipLines = this.textRenderer.wrapLines(Text.translatable("pingwheel.settings.channel.tooltip"), 140);
		}

		this.renderOrderedTooltip(matrices, tooltipLines, mouseX, mouseY);
	}

	private static List<OrderedText> getHoveredButtonTooltip(ButtonListWidget buttonList, int mouseX, int mouseY) {
		final var orderableTooltip = (OrderableTooltip)buttonList.getHoveredButton(mouseX, mouseY).orElse(null);

		if (orderableTooltip != null) {
			return orderableTooltip.getOrderedTooltip();
		}

		return Collections.emptyList();
	}

	private SimpleOption<Integer> getPingVolumeOption() {
		final var pingVolumeText = "pingwheel.settings.pingVolume";

		return new SimpleOption<>(
			pingVolumeText,
			SimpleOption.emptyTooltip(),
			(optionText, value) -> {
				if (value == 0) {
					return Text.translatable(pingVolumeText, ScreenTexts.OFF);
				}

				return Text.translatable(pingVolumeText, String.format("%s%%", value));
			},
			(new SimpleOption.ValidatingIntSliderCallbacks(0, 100)),
			Codec.intRange(0, 100),
			config.getPingVolume(),
			config::setPingVolume
		);
	}

	private SimpleOption<Integer> getPingDurationOption() {
		final var pingDurationKey = "pingwheel.settings.pingDuration";

		return new SimpleOption<>(
			pingDurationKey,
			SimpleOption.emptyTooltip(),
			(optionText, value) -> Text.translatable(pingDurationKey, String.format("%ss", value)),
			(new SimpleOption.ValidatingIntSliderCallbacks(1, 60)),
			Codec.intRange(1, 60),
			config.getPingDuration(),
			config::setPingDuration
		);
	}

	private SimpleOption<Integer> getPingDistanceOption() {
		final var pingDistanceKey = "pingwheel.settings.pingDistance";

		return new SimpleOption<>(
			pingDistanceKey,
			SimpleOption.emptyTooltip(),
			(optionText, value) -> {
				if (value == 0) {
					return Text.translatable(pingDistanceKey, Text.translatable(pingDistanceKey + ".hidden"));
				} else if (value == 2048) {
					return Text.translatable(pingDistanceKey, Text.translatable(pingDistanceKey + ".unlimited"));
				}

				return Text.translatable(pingDistanceKey, String.format("%sm", value));
			},
			(new SimpleOption.ValidatingIntSliderCallbacks(0, 128))
				.withModifier((value) -> value * 16, (value) -> value / 16),
			Codec.intRange(0, 2048),
			config.getPingDistance(),
			config::setPingDistance
		);
	}

	private SimpleOption<Integer> getCorrectionPeriodOption() {
		final var correctionPeriodKey = "pingwheel.settings.correctionPeriod";

		return new SimpleOption<>(
			correctionPeriodKey,
			SimpleOption.emptyTooltip(),
			(optionText, value) -> Text.translatable(correctionPeriodKey, String.format("%.1fs", value * 0.1f)),
			(new SimpleOption.ValidatingIntSliderCallbacks(1, 50)),
			Codec.intRange(1, 50),
			(int)(config.getCorrectionPeriod() * 10f),
			(value) -> config.setCorrectionPeriod(value * 0.1f)
		);
	}

	private SimpleOption<Boolean> getItemIconsVisibleOption() {
		return SimpleOption.ofBoolean(
			"pingwheel.settings.itemIconVisible",
			config.isItemIconVisible(),
			config::setItemIconVisible
		);
	}

	private SimpleOption<Integer> getPingSizeOption() {
		final var pingSizeKey = "pingwheel.settings.pingSize";

		return new SimpleOption<>(
			pingSizeKey,
			SimpleOption.emptyTooltip(),
			(optionText, value) -> Text.translatable(pingSizeKey, String.format("%s%%", value)),
			(new SimpleOption.ValidatingIntSliderCallbacks(4, 30))
				.withModifier((value) -> value * 10, (value) -> value / 10),
			Codec.intRange(40, 300),
			config.getPingSize(),
			config::setPingSize
		);
	}
}

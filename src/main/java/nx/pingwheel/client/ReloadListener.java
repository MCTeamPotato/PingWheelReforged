package nx.pingwheel.client;

import lombok.var;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static nx.pingwheel.shared.PingWheel.MOD_ID;

public class ReloadListener implements IdentifiableResourceReloadListener {
    public static final Identifier RELOAD_LISTENER_ID = new Identifier(MOD_ID, "reload_listener");
    public static final Identifier PING_TEXTURE_ID = new Identifier(MOD_ID, "textures/ping.png");
    @Override
    public Identifier getFabricId() {
        return RELOAD_LISTENER_ID;
    }

    @Override
    public CompletableFuture<Void> reload(Synchronizer helper, ResourceManager resourceManager, Profiler loadProfiler, Profiler applyProfiler, Executor loadExecutor, Executor applyExecutor) {
        return reloadTextures(helper, resourceManager, loadExecutor, applyExecutor);
    }

    private CompletableFuture<Void> reloadTextures(ResourceReloader.@NotNull Synchronizer helper, ResourceManager resourceManager, Executor loadExecutor, Executor applyExecutor) {
        return CompletableFuture
                .supplyAsync(() -> {
                    final var canLoadTexture = resourceManager.getResource(PING_TEXTURE_ID).isPresent();

                    if (!canLoadTexture) {
                        // force texture manager to remove the entry from its index
                        PingWheelClient.Game.getTextureManager().registerTexture(PING_TEXTURE_ID, MissingSprite.getMissingSpriteTexture());
                    }

                    return canLoadTexture;
                }, loadExecutor)
                .thenCompose(helper::whenPrepared)
                .thenAcceptAsync(canLoadTexture -> {
                    if (canLoadTexture) {
                        PingWheelClient.Game.getTextureManager().bindTexture(PING_TEXTURE_ID);
                    }
                }, applyExecutor);
    }
}

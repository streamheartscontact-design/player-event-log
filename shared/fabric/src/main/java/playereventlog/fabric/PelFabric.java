package playereventlog.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import playereventlog.core.PlayerEventLog;
import playereventlog.mc.MojmapReader;

public final class PelFabric implements ClientModInitializer {
    private static PlayerEventLog log;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        String mc = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        log = PlayerEventLog.init(loader.getGameDir().toFile(), mc, "fabric");
    }

    public static void onClientTick(Minecraft mc) {
        PlayerEventLog l = log;
        if (l == null || l.disabled()) return;
        try {
            if (l.wantsSnapshots()) l.tick(MojmapReader.read(mc));
        } catch (Throwable t) {
            l.reportError(t);
        }
    }
}

package playereventlog.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import playereventlog.core.PlayerEventLog;
import playereventlog.core.SnapshotReader;

public final class PelFabric implements ClientModInitializer {
    private static PlayerEventLog log;
    private static SnapshotReader reader;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        String mc = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        String impl = implementationFor(mc);
        if (impl == null) {
            System.out.println("[PlayerEventLog] Minecraft " + mc + " is not supported by this version: nothing is recorded");
            return;
        }
        try {
            reader = (SnapshotReader) Class.forName("playereventlog.mc." + impl + ".Reader").getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            System.err.println("[PlayerEventLog] could not load the reader for Minecraft " + mc + ", nothing is recorded: " + t);
            return;
        }
        log = PlayerEventLog.init(loader.getGameDir().toFile(), mc, "fabric");
    }

    static String implementationFor(String mc) {
        if (mc == null) return null;
        switch (mc) {
            case "1.16.5":
                return "v1_16_5";
            case "1.18.2":
                return "v1_18_2";
            case "1.20":
            case "1.20.1":
                return "v1_20_1";
            case "1.21":
            case "1.21.1":
                return "v1_21_1";
            case "1.21.11":
                return "v1_21_11";
            case "26.3":
                return "v26_3";
            default:
                return null;
        }
    }

    public static void onClientTick(Object minecraft) {
        PlayerEventLog l = log;
        if (l == null || l.disabled()) return;
        try {
            if (l.wantsSnapshots()) l.tick(reader.read(minecraft));
        } catch (Throwable t) {
            l.reportError(t);
        }
    }
}

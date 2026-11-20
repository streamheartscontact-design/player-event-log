package playereventlog.forge;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import playereventlog.core.PlayerEventLog;
import playereventlog.mc.MojmapReader;

final class ForgeClient {
    private static PlayerEventLog log;

    private ForgeClient() {
    }

    static void init() {
        log = PlayerEventLog.init(FMLPaths.GAMEDIR.get().toFile(), SharedConstants.getCurrentVersion().getName(), "forge");
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PlayerEventLog l = log;
        if (l == null || l.disabled()) return;
        try {
            if (l.wantsSnapshots()) l.tick(MojmapReader.read(Minecraft.getInstance()));
        } catch (Throwable t) {
            l.reportError(t);
        }
    }
}

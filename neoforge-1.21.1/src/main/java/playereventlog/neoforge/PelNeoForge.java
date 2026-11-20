package playereventlog.neoforge;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import playereventlog.core.ModInfo;
import playereventlog.core.PlayerEventLog;
import playereventlog.mc.MojmapReader;

@Mod(value = ModInfo.ID, dist = Dist.CLIENT)
public final class PelNeoForge {
    private static PlayerEventLog log;

    public PelNeoForge() {
        log = PlayerEventLog.init(FMLPaths.GAMEDIR.get().toFile(), SharedConstants.getCurrentVersion().getName(), "neoforge");
        NeoForge.EVENT_BUS.addListener(PelNeoForge::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        PlayerEventLog l = log;
        if (l == null || l.disabled()) return;
        try {
            if (l.wantsSnapshots()) l.tick(MojmapReader.read(Minecraft.getInstance()));
        } catch (Throwable t) {
            l.reportError(t);
        }
    }
}

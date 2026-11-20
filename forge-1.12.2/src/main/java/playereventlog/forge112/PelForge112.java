package playereventlog.forge112;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import playereventlog.core.ModInfo;
import playereventlog.core.PlayerEventLog;

@Mod(modid = ModInfo.ID, name = ModInfo.NAME, version = ModInfo.VERSION, clientSideOnly = true,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public final class PelForge112 {
    private PlayerEventLog log;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        log = PlayerEventLog.init(Loader.instance().getConfigDir().getParentFile(), Loader.MC_VERSION, "forge");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PlayerEventLog l = log;
        if (l == null || l.disabled()) return;
        try {
            if (l.wantsSnapshots()) l.tick(Reader112.read(Minecraft.getMinecraft()));
        } catch (Throwable t) {
            l.reportError(t);
        }
    }
}

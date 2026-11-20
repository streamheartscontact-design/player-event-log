package playereventlog.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import playereventlog.core.ModInfo;

@Mod(ModInfo.ID)
public final class PelForge {
    public PelForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init();
    }
}

package playereventlog.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import playereventlog.fabric.PelFabric;

@Pseudo
@Mixin(targets = {"net.minecraft.class_310", "net.minecraft.client.Minecraft"}, remap = false)
public abstract class MinecraftMixin {
    @Inject(method = {"method_1574()V", "tick()V"}, at = @At("TAIL"), remap = false, require = 0)
    private void playereventlog$afterTick(CallbackInfo ci) {
        PelFabric.onClientTick(this);
    }
}

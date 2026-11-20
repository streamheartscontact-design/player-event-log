package playereventlog.mc;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import playereventlog.core.DamageInfo;
import playereventlog.core.Snapshot;

final class Compat {
    static final boolean DAMAGE_SOURCES = false;

    private Compat() {
    }

    static Object screen(net.minecraft.client.Minecraft mc) {
        return mc.screen;
    }

    static String effectId(MobEffectInstance e) {
        ResourceLocation id = Registry.MOB_EFFECT.getKey(e.getEffect());
        return id == null ? null : id.toString();
    }

    static String effectCategory(MobEffectInstance e) {
        MobEffectCategory c = e.getEffect().getCategory();
        if (c == MobEffectCategory.BENEFICIAL) return "beneficial";
        if (c == MobEffectCategory.HARMFUL) return "harmful";
        return c == null ? null : "neutral";
    }

    static String translationKey(Component c) {
        return c instanceof TranslatableComponent ? ((TranslatableComponent) c).getKey() : null;
    }

    static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    static void fillFreeze(LocalPlayer p, Snapshot s) {
        s.frozenTicks = null;
        s.fullyFrozen = null;
    }

    static Boolean isFallFlying(LocalPlayer p) {
        return p.isFallFlying();
    }

    static String entityTypeId(Entity e) {
        return EntityType.getKey(e.getType()).toString();
    }

    static String gameMode(MultiPlayerGameMode gm) {
        GameType t = gm.getPlayerMode();
        return t == null ? null : t.getName();
    }

    static boolean onGround(Entity e) {
        return e.isOnGround();
    }

    static double minY(Level level) {
        return 0;
    }

    static DamageInfo lastDamage(LocalPlayer p) {
        return null;
    }

    static Boolean jumpInput(LocalPlayer p) {
        return p.input == null ? null : Boolean.valueOf(p.input.jumping);
    }

    static int swingStart(LocalPlayer p) {
        if (!p.swinging || p.swingTime != 0) return 0;
        return p.swingingArm == net.minecraft.world.InteractionHand.OFF_HAND ? 2 : 1;
    }
}

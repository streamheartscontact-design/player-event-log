package playereventlog.mc;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import playereventlog.core.DamageInfo;
import playereventlog.core.Snapshot;

final class Compat {
    static final boolean DAMAGE_SOURCES = true;

    private Compat() {
    }

    static Object screen(net.minecraft.client.Minecraft mc) {
        return mc.screen;
    }

    static String effectId(MobEffectInstance e) {
        return e.getEffect().unwrapKey().map(k -> k.location().toString()).orElse(null);
    }

    static String effectCategory(MobEffectInstance e) {
        return category(e.getEffect().value().getCategory());
    }

    static String category(MobEffectCategory c) {
        if (c == MobEffectCategory.BENEFICIAL) return "beneficial";
        if (c == MobEffectCategory.HARMFUL) return "harmful";
        return c == null ? null : "neutral";
    }

    static String translationKey(Component c) {
        return c.getContents() instanceof TranslatableContents ? ((TranslatableContents) c.getContents()).getKey() : null;
    }

    static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    static void fillFreeze(LocalPlayer p, Snapshot s) {
        s.frozenTicks = p.getTicksFrozen();
        s.fullyFrozen = p.isFullyFrozen();
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
        return e.onGround();
    }

    static double minY(Level level) {
        return level.getMinBuildHeight();
    }

    static DamageInfo lastDamage(LocalPlayer p) {
        DamageSource src = p.getLastDamageSource();
        if (src == null) return null;
        String type = src.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse(null);
        Entity a = src.getEntity();
        Entity d = src.getDirectEntity();
        return new DamageInfo(type, src.getMsgId(),
                a == null ? null : entityTypeId(a), a == null ? null : a.getName().getString(),
                d == null ? null : entityTypeId(d), src);
    }

    static Boolean jumpInput(LocalPlayer p) {
        return p.input == null ? null : Boolean.valueOf(p.input.jumping);
    }

    static int swingStart(LocalPlayer p) {
        if (!p.swinging || p.swingTime != 0) return 0;
        return p.swingingArm == net.minecraft.world.InteractionHand.OFF_HAND ? 2 : 1;
    }
}

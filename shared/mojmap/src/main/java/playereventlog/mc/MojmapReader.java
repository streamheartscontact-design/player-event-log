package playereventlog.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import playereventlog.core.Effect;
import playereventlog.core.Snapshot;

import java.lang.reflect.Field;
import java.util.List;

public final class MojmapReader {
    private static Field deathMessageField;
    private static float lastHealth = -1f;

    private MojmapReader() {
    }

    public static Snapshot read(Minecraft mc) {
        Snapshot s = new Snapshot();
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) {
            s.present = false;
            lastHealth = -1f;
            return s;
        }
        s.present = true;
        s.identity = System.identityHashCode(p);
        s.singleplayer = mc.hasSingleplayerServer();
        s.playerName = p.getName().getString();

        s.health = p.getHealth();
        s.maxHealth = p.getMaxHealth();
        s.absorption = p.getAbsorptionAmount();
        s.food = p.getFoodData().getFoodLevel();
        s.saturation = p.getFoodData().getSaturationLevel();
        s.armor = p.getArmorValue();
        s.air = p.getAirSupply();
        s.maxAir = p.getMaxAirSupply();
        s.xpLevel = p.experienceLevel;

        s.inWater = p.isInWater();
        s.underWater = p.isUnderWater();
        s.inLava = p.isInLava();
        s.onFire = p.isOnFire();
        Compat.fillFreeze(p, s);

        s.sleeping = p.isSleeping();
        s.fallFlying = Compat.isFallFlying(p);
        s.swimming = p.isSwimming();
        s.sneaking = p.isShiftKeyDown();
        s.sprinting = p.isSprinting();
        Entity vehicle = p.getVehicle();
        s.vehicle = vehicle == null ? null : Compat.entityTypeId(vehicle);

        s.dead = p.isDeadOrDying();
        Object screen = Compat.screen(mc);
        s.deathScreen = screen instanceof DeathScreen;
        if (s.deathScreen) readDeathMessage(screen, s);
        s.screenOpen = screen != null && !s.deathScreen;

        s.dimension = Compat.dimensionId(level);
        s.gameMode = mc.gameMode == null ? null : Compat.gameMode(mc.gameMode);

        for (MobEffectInstance e : p.getActiveEffects()) {
            s.effects.add(new Effect(Compat.effectId(e), e.getAmplifier(), e.getDuration(), e.isAmbient(),
                    Compat.effectCategory(e)));
        }

        s.hurtTime = p.hurtTime;
        s.damageSupported = Compat.DAMAGE_SOURCES;
        s.damage = Compat.lastDamage(p);
        s.fallDistance = (float) p.fallDistance;
        s.onGround = Compat.onGround(p);
        s.belowWorld = p.getY() < Compat.minY(level) - 64;

        s.positionKnown = true;
        s.x = p.getX();
        s.y = p.getY();
        s.z = p.getZ();
        s.jumpInput = Compat.jumpInput(p);
        s.swingStart = Compat.swingStart(p);
        s.attackKey = mc.options.keyAttack.isDown();
        s.useKey = mc.options.keyUse.isDown();
        s.mining = mc.gameMode != null && mc.gameMode.isDestroying();

        if (p.hurtTime > 0 || (lastHealth >= 0f && s.health < lastHealth)) readSurroundings(mc, p, level, s);
        lastHealth = s.health;
        return s;
    }

    private static void readSurroundings(Minecraft mc, LocalPlayer p, Level level, Snapshot s) {
        s.inWall = p.isInWall();
        AABB bb = p.getBoundingBox();
        int x0 = Mth.floor(bb.minX - 0.05), x1 = Mth.floor(bb.maxX + 0.05);
        int y0 = Mth.floor(bb.minY - 0.05), y1 = Mth.floor(bb.maxY);
        int z0 = Mth.floor(bb.minZ - 0.05), z1 = Mth.floor(bb.maxZ + 0.05);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block b = level.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (b == Blocks.CACTUS) s.touchingCactus = true;
                    else if (b == Blocks.SWEET_BERRY_BUSH) s.touchingBerryBush = true;
                    else if (b == Blocks.MAGMA_BLOCK && y == y0) s.onHotFloor = true;
                    else if (b == Blocks.FIRE || b == Blocks.SOUL_FIRE || b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE) {
                        s.inFireBlock = true;
                    }
                }
            }
        }
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestProjectile = false;
        List<Entity> near = level.getEntities(p, bb.inflate(4.0));
        for (Entity e : near) {
            boolean projectile = e instanceof Projectile;
            if (!(projectile || e instanceof Enemy || e instanceof Player)) continue;
            double d = e.distanceToSqr(p);
            if (projectile) d -= 4.0;
            if (d < bestDist) {
                bestDist = d;
                best = e;
                bestProjectile = projectile;
            }
        }
        if (best != null) {
            Entity owner = bestProjectile ? ((Projectile) best).getOwner() : best;
            Entity who = owner != null ? owner : best;
            s.nearbyProjectile = bestProjectile;
            s.nearbyAttacker = Compat.entityTypeId(who);
            s.nearbyAttackerName = who.getName().getString();
            s.nearbyAttackerIsPlayer = who instanceof Player;
        }
    }

    private static void readDeathMessage(Object screen, Snapshot s) {
        try {
            if (deathMessageField != null) {
                Component c = (Component) deathMessageField.get(screen);
                if (c != null) {
                    s.deathMessage = c.getString();
                    s.deathMessageKey = Compat.translationKey(c);
                }
                return;
            }
            Component fallback = null;
            for (Field f : DeathScreen.class.getDeclaredFields()) {
                if (!Component.class.isAssignableFrom(f.getType()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                Component c = (Component) f.get(screen);
                if (c == null) continue;
                String key = Compat.translationKey(c);
                if (key != null && key.startsWith("death.")) {
                    deathMessageField = f;
                    s.deathMessage = c.getString();
                    s.deathMessageKey = key;
                    return;
                }
                if (fallback == null && (key == null || !key.startsWith("deathScreen."))) fallback = c;
            }
            if (fallback != null) s.deathMessage = fallback.getString();
        } catch (Throwable ignored) {
        }
    }
}

package playereventlog.forge112;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import playereventlog.core.Effect;
import playereventlog.core.Snapshot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

final class Reader112 {
    private static Field deathMessageField;
    private static float lastHealth = -1f;

    private Reader112() {
    }

    static Snapshot read(Minecraft mc) {
        Snapshot s = new Snapshot();
        EntityPlayerSP p = mc.player;
        World w = mc.world;
        if (p == null || w == null) {
            s.present = false;
            lastHealth = -1f;
            return s;
        }
        s.present = true;
        s.identity = System.identityHashCode(p);
        s.singleplayer = mc.isSingleplayer();
        s.playerName = p.getName();

        s.health = p.getHealth();
        s.maxHealth = p.getMaxHealth();
        s.absorption = p.getAbsorptionAmount();
        s.food = p.getFoodStats().getFoodLevel();
        s.saturation = p.getFoodStats().getSaturationLevel();
        s.armor = p.getTotalArmorValue();
        s.air = p.getAir();
        s.maxAir = 300;
        s.xpLevel = p.experienceLevel;

        s.inWater = p.isInWater();
        s.underWater = p.isInsideOfMaterial(Material.WATER);
        s.inLava = p.isInLava();
        s.onFire = p.isBurning();
        s.frozenTicks = null;
        s.fullyFrozen = null;

        s.sleeping = p.isPlayerSleeping();
        s.fallFlying = p.isElytraFlying();
        s.swimming = null;
        s.sneaking = p.isSneaking();
        s.sprinting = p.isSprinting();
        Entity vehicle = p.getRidingEntity();
        s.vehicle = vehicle == null ? null : typeId(vehicle);

        s.dead = p.getHealth() <= 0f || p.isDead;
        s.deathScreen = mc.currentScreen instanceof GuiGameOver;
        if (s.deathScreen) readDeathMessage(mc.currentScreen, s);
        s.screenOpen = mc.currentScreen != null && !s.deathScreen;

        s.dimension = "minecraft:" + w.provider.getDimensionType().getName();
        s.gameMode = mc.playerController == null ? null : mc.playerController.getCurrentGameType().getName();

        for (PotionEffect e : p.getActivePotionEffects()) {
            Potion potion = e.getPotion();
            ResourceLocation id = Potion.REGISTRY.getNameForObject(potion);
            String category = potion.isBadEffect() ? "harmful" : potion.isBeneficial() ? "beneficial" : "neutral";
            s.effects.add(new Effect(id == null ? null : id.toString(), e.getAmplifier(), e.getDuration(),
                    e.getIsAmbient(), category));
        }

        s.hurtTime = p.hurtTime;
        s.damageSupported = false;
        s.damage = null;
        s.fallDistance = p.fallDistance;
        s.onGround = p.onGround;
        s.belowWorld = p.posY < -64;

        s.positionKnown = true;
        s.x = p.posX;
        s.y = p.posY;
        s.z = p.posZ;
        s.jumpInput = p.movementInput == null ? null : Boolean.valueOf(p.movementInput.jump);
        s.swingStart = p.isSwingInProgress && p.swingProgressInt == 0 ? (p.swingingHand == EnumHand.OFF_HAND ? 2 : 1) : 0;
        s.attackKey = mc.gameSettings.keyBindAttack.isKeyDown();
        s.useKey = mc.gameSettings.keyBindUseItem.isKeyDown();
        s.mining = mc.playerController != null && mc.playerController.getIsHittingBlock();

        if (p.hurtTime > 0 || (lastHealth >= 0f && s.health < lastHealth)) readSurroundings(p, w, s);
        lastHealth = s.health;
        return s;
    }

    private static String typeId(Entity e) {
        if (e instanceof EntityPlayer) return "minecraft:player";
        ResourceLocation id = EntityList.getKey(e);
        return id == null ? null : id.toString();
    }

    private static void readSurroundings(EntityPlayerSP p, World w, Snapshot s) {
        s.inWall = p.isEntityInsideOpaqueBlock();
        AxisAlignedBB bb = p.getEntityBoundingBox();
        int x0 = MathHelper.floor(bb.minX - 0.05), x1 = MathHelper.floor(bb.maxX + 0.05);
        int y0 = MathHelper.floor(bb.minY - 0.05), y1 = MathHelper.floor(bb.maxY);
        int z0 = MathHelper.floor(bb.minZ - 0.05), z1 = MathHelper.floor(bb.maxZ + 0.05);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (b == Blocks.CACTUS) s.touchingCactus = true;
                    else if (b == Blocks.MAGMA && y == y0) s.onHotFloor = true;
                    else if (b == Blocks.FIRE) s.inFireBlock = true;
                }
            }
        }
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestProjectile = false;
        List<Entity> near = w.getEntitiesWithinAABBExcludingEntity(p, bb.grow(4.0));
        for (Entity e : near) {
            boolean projectile = e instanceof IProjectile;
            if (!(projectile || e instanceof IMob || e instanceof EntityPlayer)) continue;
            double d = p.getDistanceSq(e);
            if (projectile) d -= 4.0;
            if (d < bestDist) {
                bestDist = d;
                best = e;
                bestProjectile = projectile;
            }
        }
        if (best != null) {
            Entity who = best;
            if (best instanceof EntityArrow && ((EntityArrow) best).shootingEntity != null) {
                who = ((EntityArrow) best).shootingEntity;
            }
            s.nearbyProjectile = bestProjectile;
            s.nearbyAttacker = typeId(who);
            s.nearbyAttackerName = who.getName();
            s.nearbyAttackerIsPlayer = who instanceof EntityPlayer;
        }
    }

    private static void readDeathMessage(Object screen, Snapshot s) {
        try {
            if (deathMessageField != null) {
                ITextComponent c = (ITextComponent) deathMessageField.get(screen);
                if (c != null) {
                    s.deathMessage = c.getUnformattedText();
                    s.deathMessageKey = c instanceof TextComponentTranslation ? ((TextComponentTranslation) c).getKey() : null;
                }
                return;
            }
            for (Field f : GuiGameOver.class.getDeclaredFields()) {
                if (!ITextComponent.class.isAssignableFrom(f.getType()) || Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                ITextComponent c = (ITextComponent) f.get(screen);
                if (c == null) continue;
                deathMessageField = f;
                s.deathMessage = c.getUnformattedText();
                s.deathMessageKey = c instanceof TextComponentTranslation ? ((TextComponentTranslation) c).getKey() : null;
                return;
            }
        } catch (Throwable ignored) {
        }
    }
}

package playereventlog.core;

import java.util.ArrayList;
import java.util.List;

public final class Snapshot {
    public boolean present;
    public int identity;
    public Boolean singleplayer;
    public String playerName;

    public float health;
    public float maxHealth;
    public float absorption;
    public int food;
    public float saturation;
    public int armor;
    public int air;
    public int maxAir = 300;
    public int xpLevel;

    public boolean inWater;
    public boolean underWater;
    public boolean inLava;
    public boolean onFire;
    public Integer frozenTicks;
    public Boolean fullyFrozen;

    public boolean sleeping;
    public Boolean fallFlying;
    public Boolean swimming;
    public boolean sneaking;
    public boolean sprinting;
    public String vehicle;

    public boolean dead;
    public boolean deathScreen;
    public boolean screenOpen;
    public String deathMessage;
    public String deathMessageKey;

    public String dimension;
    public String gameMode;

    public List<Effect> effects = new ArrayList<Effect>();

    public int hurtTime;
    public boolean damageSupported;
    public DamageInfo damage;

    public float fallDistance;
    public boolean onGround;
    public boolean inWall;
    public boolean touchingCactus;
    public boolean touchingBerryBush;
    public boolean onHotFloor;
    public boolean inFireBlock;
    public boolean belowWorld;
    public String nearbyAttacker;
    public String nearbyAttackerName;
    public boolean nearbyAttackerIsPlayer;
    public boolean nearbyProjectile;

    public boolean positionKnown;
    public double x;
    public double y;
    public double z;
    public Boolean jumpInput;
    public int swingStart;
    public Boolean attackKey;
    public Boolean useKey;
    public Boolean mining;

    public boolean hasEffect(String id) {
        for (Effect e : effects) {
            if (e.id != null && e.id.equals(id)) return true;
        }
        return false;
    }
}

package playereventlog.core;

public final class CauseEstimator {
    public static final class Result {
        public final String cause;
        public final String attacker;
        public final String attackerName;

        Result(String cause, String attacker, String attackerName) {
            this.cause = cause;
            this.attacker = attacker;
            this.attackerName = attackerName;
        }
    }

    private CauseEstimator() {
    }

    public static Result estimate(Snapshot prev, Snapshot cur, float recentFall) {
        if (cur.belowWorld || prev.belowWorld) return r(CauseMapper.VOID);
        if (recentFall > 3.0f) return r(CauseMapper.FALL);
        if (cur.inLava || prev.inLava) return r(CauseMapper.LAVA);
        if ((cur.underWater || prev.underWater) && (cur.air <= 0 || prev.air <= 0)) return r(CauseMapper.DROWN);
        if (cur.inWall) return r(CauseMapper.SUFFOCATION);
        if (cur.touchingCactus) return r(CauseMapper.CACTUS);
        if (cur.touchingBerryBush) return r(CauseMapper.SWEET_BERRY);
        if (Boolean.TRUE.equals(cur.fullyFrozen) || Boolean.TRUE.equals(prev.fullyFrozen)) return r(CauseMapper.FREEZE);
        if (cur.inFireBlock) return r(CauseMapper.FIRE);
        if (cur.onHotFloor && !cur.sneaking) return r(CauseMapper.HOT_FLOOR);
        if (cur.nearbyProjectile) {
            return new Result(CauseMapper.PROJECTILE, cur.nearbyAttacker, cur.nearbyAttackerName);
        }
        if (cur.nearbyAttacker != null) {
            return new Result(cur.nearbyAttackerIsPlayer ? CauseMapper.PLAYER : CauseMapper.MOB,
                    cur.nearbyAttacker, cur.nearbyAttackerName);
        }
        if (cur.onFire || prev.onFire) return r(CauseMapper.FIRE);
        if (cur.hasEffect("minecraft:wither")) return r(CauseMapper.WITHER);
        if (cur.hasEffect("minecraft:poison")) return r(CauseMapper.POISON);
        if (cur.food <= 0) return r(CauseMapper.STARVE);
        return r(CauseMapper.UNKNOWN);
    }

    private static Result r(String cause) {
        return new Result(cause, null, null);
    }
}

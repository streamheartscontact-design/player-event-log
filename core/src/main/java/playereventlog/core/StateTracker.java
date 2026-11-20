package playereventlog.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StateTracker {
    static final int SETTLE_TICKS = 10;
    static final int SETTLE_QUIET_TICKS = 40;
    static final int SETTLE_MAX_TICKS = 200;
    static final int DAMAGE_MEMORY_TICKS = 10;
    static final int LANDING_MEMORY_TICKS = 10;
    static final int DEATH_WAIT_TICKS = 20;
    static final int LAST_HURT_MEMORY_TICKS = 60;
    static final int EFFECT_REFRESH_TICKS = 5;
    static final float EPS = 0.01f;
    static final int ABSORB_WAIT_TICKS = 3;
    static final int STATE_INTERVAL_TICKS = 10;
    public static final int MAX_FOOD = 20;
    static final double MOVE_SPEED = 0.04;
    static final int MOVE_DEBOUNCE_TICKS = 4;
    static final int MOVE_AIR_TICKS = 30;
    static final double TELEPORT_DISTANCE = 2.0;
    static final int ACTION_GAP_TICKS = 5;
    static final int MINE_GRACE_TICKS = 8;

    private final boolean includeNames;

    private Snapshot last;
    private Snapshot base;
    private long tick;
    private long settleUntil;
    private long settleCap;
    private long settleLastChange;
    private boolean settleActive;
    private boolean joinPending;
    private boolean respawnPending;
    private String lastDimension;

    private Object lastDamageToken;
    private DamageInfo pendingDamage;
    private long pendingDamageTick;
    private float landingFall;
    private long landingTick = -1000;

    private boolean deathPending;
    private boolean deathEmitted;
    private long deathTick;
    private String lastHurtCause;
    private String lastHurtAttacker;
    private String lastHurtAttackerName;
    private boolean lastHurtEstimated;
    private long lastHurtTick = -1000;

    private String stateSignature;
    private long stateTick = -1000;
    private float pendingAbsorptionLoss;
    private long pendingAbsorptionTick;
    private int reportedFood;
    private float reportedSaturation;
    private int reportedBubbles;
    private final Map<String, Effect> effects = new LinkedHashMap<String, Effect>();
    private final List<Toggle> toggles = new ArrayList<Toggle>();
    private final Toggle water;
    private final Toggle submerged;
    private final Toggle drowning;
    private final Toggle lava;
    private final Toggle fire;
    private final Toggle freezing;
    private final Toggle frozen;
    private final Toggle sleep;
    private final Toggle elytra;
    private final Toggle swim;
    private final Toggle sneak;
    private final Toggle sprint;
    private final Toggle deathScreen;
    private final Toggle moving;
    private final Toggle mining;
    private int airTicks;
    private long lastJumpTick = -1000;
    private long lastSwingTick = -1000;
    private long lastMiningTick = -1000;

    private final int healMergeTicks;
    private final float healInstantMin;
    private float pendingHeal;
    private int pendingHealCount;
    private long pendingHealTick;
    private float pendingHealHealth;
    private float pendingHealMax;

    public StateTracker(boolean includeNames) {
        this(includeNames, 20, 2.0f, 8);
    }

    public StateTracker(Config c) {
        this(c.includeNames, msToTicks(c.healMergeMs), (float) c.healInstantMin, msToTicks(c.toggleDebounceMs));
    }

    public StateTracker(boolean includeNames, int healMergeTicks, float healInstantMin, int debounceTicks) {
        this.includeNames = includeNames;
        this.healMergeTicks = Math.max(0, healMergeTicks);
        this.healInstantMin = healInstantMin;
        int d = Math.max(0, debounceTicks);
        water = toggle("water_enter", "water_leave", d, d);
        submerged = toggle("submerged_start", "submerged_end", d, d);
        drowning = toggle("drowning_start", "drowning_end", 0, 0);
        lava = toggle("lava_enter", "lava_leave", 0, 2);
        fire = toggle("fire_start", "fire_end", 0, 2);
        freezing = toggle("freeze_start", "freeze_end", 0, 0);
        frozen = toggle("frozen_start", "frozen_end", 0, 0);
        sleep = toggle("sleep_start", "sleep_end", 0, 0);
        elytra = toggle("elytra_start", "elytra_end", 0, 0);
        swim = toggle("swim_start", "swim_end", d, d);
        sneak = toggle("sneak_start", "sneak_end", d, d);
        sprint = toggle("sprint_start", "sprint_end", d, d);
        deathScreen = toggle("death_screen_open", "death_screen_close", 0, 0);
        moving = toggle("move_start", "move_end", MOVE_DEBOUNCE_TICKS, MOVE_DEBOUNCE_TICKS);
        mining = toggle("mine_start", "mine_end", 0, 0);
    }

    static int msToTicks(double ms) {
        return (int) Math.round(Math.max(0, ms) / 50.0);
    }

    private Toggle toggle(String on, String off, int onDelay, int offDelay) {
        Toggle t = new Toggle(on, off, onDelay, offDelay);
        toggles.add(t);
        return t;
    }

    public void update(Snapshot s, EventSink out) {
        tick++;
        if (s == null || !s.present) {
            if (last != null) {
                flushHeal(out);
                if (!joinPending) out.emit(new Event("world_leave"));
                reset();
            }
            return;
        }
        if (last == null) {
            last = s;
            base = s;
            joinPending = true;
            startSettle();
            lastDimension = s.dimension;
            return;
        }

        trackLanding(s);
        trackDamageInfo(s);

        if (!joinPending && s.dimension != null && lastDimension != null && !s.dimension.equals(lastDimension)) {
            out.emit(new Event("dimension_change").put("from", lastDimension).put("to", s.dimension));
        }
        if (s.dimension != null) lastDimension = s.dimension;

        boolean deadNow = s.dead || s.deathScreen;
        if (s.identity != last.identity) {
            if (deathPending) emitDeath(s, out);
            if (deathEmitted && !deadNow) respawnPending = true;
            startSettle();
        } else if (deathEmitted && !deadNow && s.health > 0 && !respawnPending) {
            respawnPending = true;
            startSettle();
        }

        if (settling(s)) {
            last = s;
            return;
        }
        if (joinPending) {
            emitJoin(s, out);
            rebase(s);
            stateSignature = fullSignature(s);
            stateTick = tick;
            joinPending = false;
            last = s;
            return;
        }
        if (respawnPending) {
            if (deathScreen.known && deathScreen.state && !s.deathScreen) emit(out, deathScreen.offEvent);
            out.emit(new Event("respawn").put("health", s.health).put("max_health", s.maxHealth)
                    .put("food", s.food).put("dimension", s.dimension));
            rebase(s);
            emitState(s, out);
            respawnPending = false;
            deathEmitted = false;
            deathPending = false;
            last = s;
            return;
        }

        diffHealth(s, out);
        diffDeath(s, out, deadNow);
        diffStats(s, out);
        diffEffects(s, out);
        diffToggles(s, out);
        diffVehicle(s, out);
        diffActions(s, out);
        if (pendingHealCount > 0 && tick - pendingHealTick >= healMergeTicks) flushHeal(out);
        if (tick - stateTick >= STATE_INTERVAL_TICKS && !fullSignature(s).equals(stateSignature)) emitState(s, out);

        base = s;
        last = s;
    }

    private void startSettle() {
        settleUntil = tick + SETTLE_TICKS;
        settleCap = tick + SETTLE_MAX_TICKS;
        settleLastChange = tick;
        settleActive = true;
    }

    private boolean settling(Snapshot s) {
        if (!settleActive) return false;
        if (s.screenOpen || !sameCoreValues(s, last)) settleLastChange = tick;
        if (tick < settleCap && (tick < settleUntil || tick - settleLastChange < SETTLE_QUIET_TICKS)) return true;
        settleActive = false;
        return false;
    }

    private static boolean sameCoreValues(Snapshot a, Snapshot b) {
        return a.health == b.health && a.maxHealth == b.maxHealth && a.absorption == b.absorption
                && a.food == b.food && a.armor == b.armor && a.xpLevel == b.xpLevel
                && a.effects.size() == b.effects.size()
                && (a.dimension == null ? b.dimension == null : a.dimension.equals(b.dimension));
    }

    private void reset() {
        last = null;
        base = null;
        joinPending = false;
        respawnPending = false;
        settleActive = false;
        deathPending = false;
        deathEmitted = false;
        pendingDamage = null;
        lastDamageToken = null;
        lastDimension = null;
        stateSignature = null;
        effects.clear();
        for (Toggle t : toggles) t.known = false;
        airTicks = 0;
    }

    private void rebase(Snapshot s) {
        base = s;
        reportedFood = s.food;
        reportedSaturation = s.saturation;
        reportedBubbles = bubbles(s);
        effects.clear();
        for (Effect e : s.effects) if (e.id != null) effects.put(e.id, e);
        for (Toggle t : toggles) t.known = false;
        applyToggles(s, null);
        pendingDamage = null;
        pendingAbsorptionLoss = 0f;
        pendingHeal = 0f;
        pendingHealCount = 0;
    }

    private void trackLanding(Snapshot s) {
        if (last.fallDistance > 0.5f && s.fallDistance < last.fallDistance - 0.01f) {
            landingFall = last.fallDistance;
            landingTick = tick;
        }
    }

    private float recentFall() {
        return tick - landingTick <= LANDING_MEMORY_TICKS ? landingFall : 0f;
    }

    private void trackDamageInfo(Snapshot s) {
        if (s.damage != null && s.damage.token != lastDamageToken) {
            pendingDamage = s.damage;
            pendingDamageTick = tick;
            lastDamageToken = s.damage.token;
        }
    }

    private void emitJoin(Snapshot s, EventSink out) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Effect e : s.effects) list.add(effectFields(new Event("x"), e).fields);
        Event e = new Event("world_join")
                .put("singleplayer", s.singleplayer)
                .put("player", includeNames ? s.playerName : null)
                .put("dimension", s.dimension)
                .put("gamemode", s.gameMode)
                .put("health", s.health)
                .put("max_health", s.maxHealth)
                .put("absorption", s.absorption)
                .put("food", s.food)
                .put("saturation", s.saturation)
                .put("armor", s.armor)
                .put("air", s.air)
                .put("max_air", s.maxAir)
                .put("xp_level", s.xpLevel)
                .put("effects", list);
        out.emit(e);
    }

    private void emitState(Snapshot s, EventSink out) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Effect e : s.effects) list.add(effectFields(new Event("x"), e).fields);
        out.emit(new Event("state")
                .put("health", s.health)
                .put("max_health", s.maxHealth)
                .put("absorption", s.absorption)
                .put("food", s.food)
                .put("max_food", MAX_FOOD)
                .put("saturation", s.saturation)
                .put("armor", s.armor)
                .put("air", s.air)
                .put("max_air", s.maxAir)
                .put("xp_level", s.xpLevel)
                .put("dimension", s.dimension)
                .put("gamemode", s.gameMode)
                .put("alive", !(s.dead || s.deathScreen))
                .put("in_water", s.inWater)
                .put("submerged", s.underWater)
                .put("in_lava", s.inLava)
                .put("on_fire", s.onFire)
                .put("frozen_ticks", s.frozenTicks)
                .put("fully_frozen", s.fullyFrozen)
                .put("sleeping", s.sleeping)
                .put("elytra", s.fallFlying)
                .put("swimming", s.swimming)
                .put("sprinting", s.sprinting)
                .put("vehicle", s.vehicle)
                .put("moving", moving.known ? Boolean.valueOf(moving.state) : null)
                .put("sneaking", s.sneaking)
                .put("on_ground", s.onGround)
                .put("effects", list));
        stateSignature = fullSignature(s);
        stateTick = tick;
    }

    private String fullSignature(Snapshot s) {
        return signature(s) + '|' + (moving.known ? Boolean.valueOf(moving.state) : null) + s.sneaking + s.onGround;
    }

    static String signature(Snapshot s) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(Math.round(s.health * 100)).append('|').append(Math.round(s.maxHealth * 100)).append('|')
                .append(Math.round(s.absorption * 100)).append('|').append(s.food).append('|')
                .append((int) (s.saturation * 2)).append('|').append(s.armor).append('|').append(bubbles(s)).append('|')
                .append(s.xpLevel).append('|').append(s.dimension).append('|').append(s.gameMode).append('|')
                .append(s.dead || s.deathScreen).append(s.inWater).append(s.underWater).append(s.inLava).append(s.onFire)
                .append(s.frozenTicks != null && s.frozenTicks > 0).append(s.fullyFrozen).append(s.sleeping)
                .append(s.fallFlying).append(s.swimming).append(s.sprinting).append('|').append(s.vehicle);
        for (Effect e : s.effects) sb.append('|').append(e.id).append(':').append(e.amplifier).append(e.duration == -1);
        return sb.toString();
    }

    private void diffHealth(Snapshot s, EventSink out) {
        float dH = s.health - base.health;
        float dAbs = s.absorption - base.absorption;
        float dMax = s.maxHealth - base.maxHealth;
        boolean freshHit = s.hurtTime > last.hurtTime
                || (pendingDamage != null && tick - pendingDamageTick <= ABSORB_WAIT_TICKS);
        boolean damagePacket = pendingDamage != null && tick - pendingDamageTick <= ABSORB_WAIT_TICKS;
        boolean clampHit = s.damageSupported ? damagePacket : freshHit;
        boolean clampedByMax = dMax < -EPS && s.health >= s.maxHealth - EPS;
        boolean absorptionHandled = false;

        if (dH < -EPS && !(clampedByMax && !clampHit)) {
            float amount = -dH;
            if (dAbs < -EPS) {
                amount += -dAbs;
                absorptionHandled = true;
            }
            emitHurt(amount + takePendingAbsorption(), s, out);
        } else if (dAbs < -EPS && dH > -EPS) {
            absorptionHandled = true;
            if (freshHit) {
                emitHurt(-dAbs + takePendingAbsorption(), s, out);
            } else {
                if (pendingAbsorptionLoss <= 0f) pendingAbsorptionTick = tick;
                pendingAbsorptionLoss += -dAbs;
            }
        } else if (pendingAbsorptionLoss > 0f && freshHit) {
            emitHurt(takePendingAbsorption(), s, out);
        } else if (dH > EPS && !(s.dead || s.deathScreen)) {
            addHeal(dH, s, out);
        }
        if (pendingAbsorptionLoss > 0f && tick - pendingAbsorptionTick >= ABSORB_WAIT_TICKS) {
            out.emit(new Event("absorption").put("absorption", s.absorption).put("delta", -pendingAbsorptionLoss));
            pendingAbsorptionLoss = 0f;
        }
        if (!absorptionHandled && Math.abs(dAbs) > EPS) {
            out.emit(new Event("absorption").put("absorption", s.absorption).put("delta", dAbs));
        }
        if (Math.abs(dMax) > EPS) {
            out.emit(new Event("max_health").put("max_health", s.maxHealth).put("delta", dMax).put("health", s.health));
        }
    }

    private void addHeal(float amount, Snapshot s, EventSink out) {
        if (amount >= healInstantMin || healMergeTicks == 0) {
            flushHeal(out);
            out.emit(new Event("heal").put("amount", amount).put("health", s.health).put("max_health", s.maxHealth)
                    .put("count", 1));
            return;
        }
        if (pendingHealCount == 0) pendingHealTick = tick;
        pendingHeal += amount;
        pendingHealCount++;
        pendingHealHealth = s.health;
        pendingHealMax = s.maxHealth;
    }

    private void flushHeal(EventSink out) {
        if (pendingHealCount == 0) return;
        out.emit(new Event("heal").put("amount", pendingHeal).put("health", pendingHealHealth)
                .put("max_health", pendingHealMax).put("count", pendingHealCount));
        pendingHeal = 0f;
        pendingHealCount = 0;
    }

    private float takePendingAbsorption() {
        float v = pendingAbsorptionLoss;
        pendingAbsorptionLoss = 0f;
        return v;
    }

    private void emitHurt(float amount, Snapshot s, EventSink out) {
        flushHeal(out);
        Event e = new Event("hurt")
                .put("amount", amount)
                .put("health", s.health)
                .put("max_health", s.maxHealth)
                .put("absorption", s.absorption);
        DamageInfo d = pendingDamage != null && tick - pendingDamageTick <= DAMAGE_MEMORY_TICKS ? pendingDamage : null;
        String cause;
        String attacker;
        String attackerName;
        boolean estimated;
        if (d != null) {
            cause = CauseMapper.fromDamage(d.typeId, d.msgId, d.attacker);
            if (CauseMapper.MAGIC.equals(cause) && s.hasEffect("minecraft:poison")) cause = CauseMapper.POISON;
            attacker = d.attacker;
            attackerName = d.attackerName;
            estimated = false;
            e.put("cause", cause).put("damage_type", d.typeId != null ? d.typeId : d.msgId)
                    .put("attacker", attacker).put("attacker_name", includeNames ? attackerName : null)
                    .put("direct", d.direct).put("estimated", false);
            pendingDamage = null;
        } else {
            CauseEstimator.Result r = CauseEstimator.estimate(last, s, recentFall());
            cause = r.cause;
            attacker = r.attacker;
            attackerName = r.attackerName;
            estimated = true;
            e.put("cause", cause).put("damage_type", null)
                    .put("attacker", attacker).put("attacker_name", includeNames ? attackerName : null)
                    .put("direct", null).put("estimated", true);
        }
        lastHurtCause = cause;
        lastHurtAttacker = attacker;
        lastHurtAttackerName = attackerName;
        lastHurtEstimated = estimated;
        lastHurtTick = tick;
        out.emit(e);
    }

    private void diffDeath(Snapshot s, EventSink out, boolean deadNow) {
        boolean deadBefore = base.dead || base.deathScreen;
        if (deadNow && !deadBefore && !deathEmitted && !deathPending) {
            deathPending = true;
            deathTick = tick;
        }
        if (deathPending && (s.deathMessage != null || tick - deathTick >= DEATH_WAIT_TICKS)) {
            emitDeath(s, out);
        }
    }

    private void emitDeath(Snapshot s, EventSink out) {
        flushHeal(out);
        String cause = CauseMapper.fromDeathKey(s.deathMessageKey);
        boolean estimated = false;
        boolean recentHurt = tick - lastHurtTick <= LAST_HURT_MEMORY_TICKS;
        String attacker = recentHurt ? lastHurtAttacker : null;
        String attackerName = recentHurt ? lastHurtAttackerName : null;
        if (cause == null || CauseMapper.UNKNOWN.equals(cause)) {
            if (recentHurt && lastHurtCause != null) {
                cause = lastHurtCause;
                estimated = lastHurtEstimated;
            } else if (s.damage != null) {
                cause = CauseMapper.fromDamage(s.damage.typeId, s.damage.msgId, s.damage.attacker);
                attacker = s.damage.attacker;
                attackerName = s.damage.attackerName;
            } else {
                cause = CauseMapper.UNKNOWN;
                estimated = true;
            }
        }
        Event death = new Event("death");
        if (includeNames) death.put("message", s.deathMessage);
        out.emit(death
                .put("message_key", s.deathMessageKey)
                .put("cause", cause)
                .put("attacker", attacker)
                .put("attacker_name", includeNames ? attackerName : null)
                .put("estimated", estimated)
                .put("dimension", s.dimension)
                .put("xp_level", s.xpLevel));
        deathPending = false;
        deathEmitted = true;
    }

    private void diffStats(Snapshot s, EventSink out) {
        if (s.food != reportedFood || Math.abs(s.saturation - reportedSaturation) >= 1.0f) {
            out.emit(new Event("food").put("food", s.food).put("max_food", MAX_FOOD).put("saturation", s.saturation)
                    .put("delta", s.food - reportedFood));
            reportedFood = s.food;
            reportedSaturation = s.saturation;
        }
        if (s.armor != base.armor) {
            out.emit(new Event("armor").put("armor", s.armor).put("delta", s.armor - base.armor));
        }
        int b = bubbles(s);
        if (b != reportedBubbles) {
            out.emit(new Event("air").put("air", s.air).put("max_air", s.maxAir).put("bubbles", b));
            reportedBubbles = b;
        }
        if (s.xpLevel != base.xpLevel) {
            out.emit(new Event(s.xpLevel > base.xpLevel ? "level_up" : "level_down")
                    .put("level", s.xpLevel).put("delta", s.xpLevel - base.xpLevel));
        }
        if (s.gameMode != null && base.gameMode != null && !s.gameMode.equals(base.gameMode)) {
            out.emit(new Event("gamemode_change").put("from", base.gameMode).put("to", s.gameMode));
        }
    }

    static int bubbles(Snapshot s) {
        if (s.maxAir <= 0) return 0;
        if (s.air <= 0) return 0;
        int b = (int) Math.ceil(s.air * 10.0 / s.maxAir);
        return Math.max(0, Math.min(10, b));
    }

    private void diffEffects(Snapshot s, EventSink out) {
        Map<String, Effect> now = new HashMap<String, Effect>();
        for (Effect e : s.effects) {
            if (e.id == null) continue;
            now.put(e.id, e);
            Effect known = effects.get(e.id);
            if (known == null) {
                out.emit(effectFields(new Event("effect_add"), e));
            } else if (known.amplifier != e.amplifier) {
                out.emit(effectFields(new Event("effect_change"), e).put("prev_amplifier", known.amplifier)
                        .put("prev_duration", known.duration));
            } else if ((known.duration == -1) != (e.duration == -1)
                    || (e.duration != -1 && e.duration > known.duration + EFFECT_REFRESH_TICKS)) {
                out.emit(effectFields(new Event("effect_change"), e).put("prev_amplifier", known.amplifier)
                        .put("prev_duration", known.duration));
            }
            effects.put(e.id, e);
        }
        Iterator<Map.Entry<String, Effect>> it = effects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Effect> en = it.next();
            if (!now.containsKey(en.getKey())) {
                Effect gone = en.getValue();
                out.emit(new Event("effect_remove").put("effect", gone.id).put("amplifier", gone.amplifier)
                        .put("level", gone.amplifier + 1).put("category", gone.category));
                it.remove();
            }
        }
    }

    static Event effectFields(Event ev, Effect e) {
        boolean infinite = e.duration == -1;
        return ev.put("effect", e.id)
                .put("amplifier", e.amplifier)
                .put("level", e.amplifier + 1)
                .put("duration", e.duration)
                .put("seconds", infinite ? null : Float.valueOf(e.duration / 20.0f))
                .put("infinite", infinite)
                .put("ambient", e.ambient)
                .put("category", e.category);
    }

    private void diffToggles(Snapshot s, EventSink out) {
        applyToggles(s, out);
    }

    private void applyToggles(Snapshot s, EventSink out) {
        emit(out, water.update(s.inWater));
        emit(out, submerged.update(s.underWater));
        emit(out, drowning.update(s.underWater && s.air <= 0));
        emit(out, lava.update(s.inLava));
        emit(out, fire.update(s.onFire));
        emit(out, freezing.update(s.frozenTicks == null ? null : Boolean.valueOf(s.frozenTicks > 0)));
        emit(out, frozen.update(s.fullyFrozen));
        emit(out, sleep.update(s.sleeping));
        emit(out, elytra.update(s.fallFlying));
        emit(out, swim.update(s.swimming));
        emit(out, sneak.update(s.sneaking));
        emit(out, sprint.update(s.sprinting));
        emit(out, deathScreen.update(s.deathScreen));
        String m = moving.update(movingNow(s));
        if (m != null && out != null) {
            Event e = new Event(m).put("on", m.equals(moving.onEvent));
            if (m.equals(moving.onEvent)) e.put("pace", s.sprinting ? "sprint" : "walk");
            out.emit(e);
        }
        emit(out, mining.update(s.mining));
    }

    private Boolean movingNow(Snapshot s) {
        if (!s.positionKnown) return null;
        if (s.vehicle != null || s.sleeping || s.dead || s.deathScreen) {
            airTicks = 0;
            return Boolean.FALSE;
        }
        Boolean keep = moving.known ? Boolean.valueOf(moving.state) : null;
        if (last == null || !last.positionKnown || last.identity != s.identity) return keep;
        double dx = s.x - last.x;
        double dz = s.z - last.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h > TELEPORT_DISTANCE) return keep;
        if (s.onGround) {
            airTicks = 0;
            return Boolean.valueOf(h > MOVE_SPEED);
        }
        if (++airTicks <= MOVE_AIR_TICKS) return keep;
        return Boolean.FALSE;
    }

    private void diffActions(Snapshot s, EventSink out) {
        if (s.positionKnown && last.positionKnown && s.identity == last.identity) {
            double dy = s.y - last.y;
            boolean input = s.jumpInput != null ? s.jumpInput.booleanValue() : (dy > 0.3 && s.hurtTime == 0);
            if (input && last.onGround && !s.onGround && dy > 0.1 && dy < TELEPORT_DISTANCE && s.vehicle == null
                    && !s.inWater && !s.inLava && !s.dead && tick - lastJumpTick >= ACTION_GAP_TICKS) {
                out.emit(new Event("jump"));
                lastJumpTick = tick;
            }
        }
        if (Boolean.TRUE.equals(s.mining)) lastMiningTick = tick;
        boolean miningNow = Boolean.TRUE.equals(s.mining) || Boolean.TRUE.equals(last.mining)
                || (Boolean.TRUE.equals(s.attackKey) && tick - lastMiningTick <= MINE_GRACE_TICKS);
        if (s.swingStart != 0 && !miningNow && !s.dead && tick - lastSwingTick >= ACTION_GAP_TICKS) {
            out.emit(new Event("swing").put("hand", s.swingStart == 2 ? "off" : "main").put("action", swingAction(s)));
            lastSwingTick = tick;
        }
    }

    private String swingAction(Snapshot s) {
        if (s.swingStart == 2) return "use";
        boolean attack = Boolean.TRUE.equals(s.attackKey);
        boolean use = Boolean.TRUE.equals(s.useKey);
        if (use && !attack) return "use";
        if (attack) return "attack";
        return Boolean.TRUE.equals(last.useKey) && !Boolean.TRUE.equals(last.attackKey) ? "use" : "attack";
    }

    private void emit(EventSink out, String name) {
        if (out == null || name == null) return;
        Event e = new Event(name);
        for (Toggle t : toggles) {
            if (name.equals(t.onEvent)) e.put("on", true);
            else if (name.equals(t.offEvent)) e.put("on", false);
        }
        out.emit(e);
    }

    private void diffVehicle(Snapshot s, EventSink out) {
        String a = base.vehicle;
        String b = s.vehicle;
        if (a == null ? b == null : a.equals(b)) return;
        if (a != null) out.emit(new Event("dismount").put("vehicle", a));
        if (b != null) out.emit(new Event("mount").put("vehicle", b));
    }

    static final class Toggle {
        final String onEvent;
        final String offEvent;
        final int onDelay;
        final int offDelay;
        boolean known;
        boolean state;
        int pending;

        Toggle(String onEvent, String offEvent, int onDelay, int offDelay) {
            this.onEvent = onEvent;
            this.offEvent = offEvent;
            this.onDelay = onDelay;
            this.offDelay = offDelay;
        }

        String update(Boolean value) {
            if (value == null) {
                known = false;
                return null;
            }
            boolean v = value.booleanValue();
            if (!known) {
                known = true;
                state = v;
                pending = 0;
                return null;
            }
            if (v == state) {
                pending = 0;
                return null;
            }
            if (++pending > (v ? onDelay : offDelay)) {
                state = v;
                pending = 0;
                return v ? onEvent : offEvent;
            }
            return null;
        }
    }
}

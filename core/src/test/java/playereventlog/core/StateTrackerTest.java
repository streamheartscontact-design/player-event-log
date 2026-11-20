package playereventlog.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StateTrackerTest {
    static final int DEBOUNCE = 8;
    private StateTracker tracker;
    private final List<Event> events = new ArrayList<Event>();
    private final List<Event> states = new ArrayList<Event>();

    static Snapshot snap() {
        Snapshot s = new Snapshot();
        s.present = true;
        s.identity = 1;
        s.health = 20f;
        s.maxHealth = 20f;
        s.food = 20;
        s.saturation = 5f;
        s.air = 300;
        s.maxAir = 300;
        s.dimension = "minecraft:overworld";
        s.gameMode = "survival";
        s.onGround = true;
        s.fallFlying = false;
        s.swimming = false;
        s.frozenTicks = 0;
        s.fullyFrozen = false;
        return s;
    }

    static Snapshot copy(Snapshot o) {
        Snapshot s = snap();
        s.identity = o.identity;
        s.health = o.health;
        s.maxHealth = o.maxHealth;
        s.absorption = o.absorption;
        s.food = o.food;
        s.saturation = o.saturation;
        s.armor = o.armor;
        s.air = o.air;
        s.xpLevel = o.xpLevel;
        s.inWater = o.inWater;
        s.underWater = o.underWater;
        s.inLava = o.inLava;
        s.onFire = o.onFire;
        s.dimension = o.dimension;
        s.gameMode = o.gameMode;
        s.effects = new ArrayList<Effect>(o.effects);
        s.vehicle = o.vehicle;
        s.sprinting = o.sprinting;
        s.sneaking = o.sneaking;
        s.swimming = o.swimming;
        s.sleeping = o.sleeping;
        s.fallFlying = o.fallFlying;
        s.frozenTicks = o.frozenTicks;
        s.fullyFrozen = o.fullyFrozen;
        s.dead = o.dead;
        s.deathScreen = o.deathScreen;
        s.damageSupported = o.damageSupported;
        s.damage = o.damage;
        return s;
    }

    @BeforeEach
    void setUp() {
        tracker = new StateTracker(false, 0, 2f, DEBOUNCE);
        events.clear();
        states.clear();
    }

    private List<Event> tick(Snapshot s) {
        int from = events.size();
        tracker.update(s, new EventSink() {
            @Override
            public void emit(Event event) {
                if (event.name.equals("state")) states.add(event);
                else events.add(event);
            }
        });
        return new ArrayList<Event>(events.subList(from, events.size()));
    }

    private List<Event> tickN(Snapshot s, int n) {
        List<Event> out = new ArrayList<Event>();
        for (int i = 0; i < n; i++) out.addAll(tick(s));
        return out;
    }

    private Snapshot joined() {
        Snapshot s = snap();
        List<Event> out = tickN(s, StateTracker.SETTLE_QUIET_TICKS + 1);
        assertEquals(1, out.size(), "only world_join: " + out);
        assertEquals("world_join", out.get(0).name);
        return s;
    }

    private static Event only(List<Event> list, String name) {
        Event found = null;
        for (Event e : list) {
            if (e.name.equals(name)) {
                assertNull(found, "duplicate " + name + " in " + list);
                found = e;
            }
        }
        assertNotNull(found, name + " missing in " + list);
        return found;
    }

    private static List<String> names(List<Event> list) {
        List<String> n = new ArrayList<String>();
        for (Event e : list) n.add(e.name);
        return n;
    }

    @Test
    void joinWaitsForSettleAndUnchangedStateIsSilent() {
        Snapshot s = snap();
        assertTrue(tickN(s, StateTracker.SETTLE_QUIET_TICKS).isEmpty());
        List<Event> out = tick(s);
        Event join = only(out, "world_join");
        assertEquals(20f, join.get("health"));
        assertEquals("minecraft:overworld", join.get("dimension"));
        assertNull(join.get("player"), "names off by default");
        assertTrue(tickN(copy(s), 200).isEmpty(), "same state never repeats");
    }

    @Test
    void defaultValuesDuringJoinAreNotReportedAsChanges() {
        Snapshot first = snap();
        tick(first);
        Snapshot real = copy(first);
        real.health = 7f;
        tickN(real, StateTracker.SETTLE_QUIET_TICKS + 2);
        assertEquals(7f, events.get(0).get("health"));
        assertEquals(1, events.size());
    }

    @Test
    void hurtWithRealDamageSource() {
        Snapshot s = joined();
        Snapshot hit = copy(s);
        hit.damageSupported = true;
        hit.damage = new DamageInfo("minecraft:mob_attack", "mob", "minecraft:zombie", "Zombie", "minecraft:zombie", new Object());
        hit.hurtTime = 10;
        hit.health = 16f;
        Event e = only(tick(hit), "hurt");
        assertEquals(4f, e.get("amount"));
        assertEquals(16f, e.get("health"));
        assertEquals("mob", e.get("cause"));
        assertEquals("minecraft:zombie", e.get("attacker"));
        assertEquals("minecraft:mob_attack", e.get("damage_type"));
        assertEquals(false, e.get("estimated"));
        assertNull(e.get("attacker_name"));
        Snapshot later = copy(hit);
        later.hurtTime = 0;
        tickN(later, 30);
        Snapshot burn = copy(later);
        burn.onFire = true;
        burn.health = 15f;
        burn.hurtTime = 10;
        Event e2 = only(tick(burn), "hurt");
        assertEquals("fire", e2.get("cause"));
        assertEquals(true, e2.get("estimated"));
    }

    @Test
    void damageSourceArrivingOneTickBeforeHealthIsUsed() {
        Snapshot s = joined();
        Snapshot pkt = copy(s);
        pkt.damage = new DamageInfo("minecraft:player_attack", "player", "minecraft:player", "Steve", null, new Object());
        pkt.hurtTime = 10;
        assertFalse(names(tick(pkt)).contains("hurt"));
        Snapshot hp = copy(pkt);
        hp.hurtTime = 9;
        hp.health = 13f;
        Event e = only(tick(hp), "hurt");
        assertEquals("player", e.get("cause"));
        assertEquals(7f, e.get("amount"));
    }

    @Test
    void estimatedCausesOnOldVersions() {
        Snapshot s = joined();
        Snapshot l = copy(s);
        l.inLava = true;
        tick(l);
        Snapshot l2 = copy(l);
        l2.health = 16f;
        l2.hurtTime = 10;
        Event e = only(tick(l2), "hurt");
        assertEquals("lava", e.get("cause"));
        assertEquals(true, e.get("estimated"));
        assertNull(e.get("damage_type"));
    }

    @Test
    void fallIsEstimatedFromLanding() {
        Snapshot s = joined();
        Snapshot air = copy(s);
        air.onGround = false;
        air.fallDistance = 8f;
        tick(air);
        Snapshot land = copy(s);
        land.fallDistance = 0f;
        tick(land);
        Snapshot dmg = copy(land);
        dmg.health = 15f;
        dmg.hurtTime = 10;
        assertEquals("fall", only(tick(dmg), "hurt").get("cause"));
    }

    @Test
    void drowningAndWaterToggles() {
        Snapshot s = joined();
        Snapshot w = copy(s);
        w.inWater = true;
        assertEquals(java.util.Arrays.asList("water_enter"), names(tickN(w, DEBOUNCE + 1)));
        Snapshot under = copy(w);
        under.underWater = true;
        under.air = 299;
        List<String> n = names(tickN(under, DEBOUNCE + 1));
        assertTrue(n.contains("submerged_start"));
        assertFalse(n.contains("air"), "10 bubbles still: " + n);
        Snapshot low = copy(under);
        low.air = 140;
        Event air = only(tick(low), "air");
        assertEquals(5, air.get("bubbles"));
        Snapshot none = copy(low);
        none.air = 0;
        List<String> n2 = names(tick(none));
        assertTrue(n2.contains("drowning_start"), n2.toString());
        Snapshot dmg = copy(none);
        dmg.air = -20;
        dmg.health = 18f;
        dmg.hurtTime = 10;
        assertEquals("drown", only(tick(dmg), "hurt").get("cause"));
        Snapshot bob = copy(dmg);
        bob.underWater = false;
        bob.air = 0;
        bob.hurtTime = 9;
        List<String> n3 = names(tick(bob));
        assertFalse(n3.contains("submerged_end"), n3.toString());
        Snapshot back = copy(dmg);
        back.hurtTime = 8;
        assertFalse(names(tick(back)).contains("submerged_start"));
    }

    @Test
    void healAbsorptionAndMaxHealth() {
        Snapshot s = joined();
        Snapshot hurt = copy(s);
        hurt.health = 10f;
        hurt.hurtTime = 10;
        tick(hurt);
        Snapshot heal = copy(hurt);
        heal.health = 11f;
        Event h = only(tick(heal), "heal");
        assertEquals(1f, h.get("amount"));
        Snapshot abs = copy(heal);
        abs.absorption = 4f;
        assertEquals(4f, only(tick(abs), "absorption").get("delta"));
        Snapshot absHit = copy(abs);
        absHit.absorption = 1f;
        absHit.hurtTime = 10;
        List<Event> out = tick(absHit);
        assertEquals(3f, only(out, "hurt").get("amount"));
        assertFalse(names(out).contains("absorption"));
        Snapshot boost = copy(absHit);
        boost.hurtTime = 0;
        boost.maxHealth = 24f;
        boost.health = 24f;
        tickN(boost, 2);
        Snapshot end = copy(boost);
        end.maxHealth = 20f;
        end.health = 20f;
        end.damageSupported = true;
        end.hurtTime = 10;
        List<Event> out2 = tick(end);
        assertFalse(names(out2).contains("hurt"), out2.toString());
        assertEquals(-4f, only(out2, "max_health").get("delta"));
    }

    @Test
    void absorptionHitWhoseDamagePacketComesLater() {
        Snapshot s = joined();
        Snapshot abs = copy(s);
        abs.absorption = 8f;
        tick(abs);
        Snapshot drop = copy(abs);
        drop.absorption = 6f;
        assertTrue(tick(drop).isEmpty());
        Snapshot pkt = copy(drop);
        pkt.damage = new DamageInfo("minecraft:drown", "drown", null, null, null, new Object());
        Event h = only(tick(pkt), "hurt");
        assertEquals(2f, h.get("amount"));
        assertEquals("drown", h.get("cause"));
        Snapshot gone = copy(pkt);
        gone.absorption = 0f;
        assertTrue(tick(gone).isEmpty());
        List<Event> later = tickN(copy(gone), StateTracker.ABSORB_WAIT_TICKS);
        assertEquals(-6f, only(later, "absorption").get("delta"));
        assertFalse(names(later).contains("hurt"));
    }

    @Test
    void effectsAddChangeRefreshRemove() {
        Snapshot s = joined();
        Snapshot a = copy(s);
        a.effects.add(new Effect("minecraft:speed", 0, 600, false, "beneficial"));
        Event add = only(tick(a), "effect_add");
        assertEquals("minecraft:speed", add.get("effect"));
        assertEquals(1, add.get("level"));
        assertEquals(30f, add.get("seconds"));
        Snapshot down = copy(a);
        down.effects.clear();
        down.effects.add(new Effect("minecraft:speed", 0, 599, false, "beneficial"));
        assertTrue(tick(down).isEmpty());
        Snapshot up = copy(down);
        up.effects.clear();
        up.effects.add(new Effect("minecraft:speed", 1, 599, false, "beneficial"));
        Event ch = only(tick(up), "effect_change");
        assertEquals(0, ch.get("prev_amplifier"));
        assertEquals(2, ch.get("level"));
        Snapshot ref = copy(up);
        ref.effects.clear();
        ref.effects.add(new Effect("minecraft:speed", 1, 1200, false, "beneficial"));
        assertEquals(1200, only(tick(ref), "effect_change").get("duration"));
        Snapshot inf = copy(ref);
        inf.effects.add(new Effect("minecraft:night_vision", 0, -1, false, "beneficial"));
        Event infAdd = only(tick(inf), "effect_add");
        assertEquals(true, infAdd.get("infinite"));
        assertNull(infAdd.get("seconds"));
        Snapshot gone = copy(inf);
        gone.effects.clear();
        List<Event> out = tick(gone);
        assertEquals(2, out.size());
        assertEquals("effect_remove", out.get(0).name);
    }

    @Test
    void deathWithMessageThenRespawn() {
        Snapshot s = joined();
        Snapshot hit = copy(s);
        hit.health = 0f;
        hit.dead = true;
        hit.hurtTime = 10;
        hit.inLava = true;
        List<Event> out = tick(hit);
        assertEquals("lava", only(out, "hurt").get("cause"));
        assertFalse(names(out).contains("death"), "waits for the death screen message");
        Snapshot screen = copy(hit);
        screen.deathScreen = true;
        screen.deathMessage = "Steve tried to swim in lava";
        screen.deathMessageKey = "death.attack.lava";
        List<Event> out2 = tick(screen);
        Event d = only(out2, "death");
        assertEquals("lava", d.get("cause"));
        assertEquals(false, d.get("estimated"));
        assertFalse(d.fields.containsKey("message"), "death message holds names: left out without include_names");
        assertEquals("death.attack.lava", d.get("message_key"));
        assertFalse(d.toString().contains("Steve"));
        assertTrue(names(out2).contains("death_screen_open"));
        assertTrue(tickN(screen, 50).isEmpty(), "dead state does not repeat");
        Snapshot fresh = snap();
        fresh.identity = 2;
        List<Event> out3 = tickN(fresh, StateTracker.SETTLE_QUIET_TICKS + 1);
        assertEquals(java.util.Arrays.asList("death_screen_close", "respawn"), names(out3), out3.toString());
        assertTrue(tickN(fresh, 20).isEmpty());
    }

    @Test
    void namesOnlyWithIncludeNames() {
        tracker = new StateTracker(true, 0, 2f, DEBOUNCE);
        Snapshot s = snap();
        s.playerName = "Steve";
        List<Event> join = tickN(s, StateTracker.SETTLE_QUIET_TICKS + 1);
        assertEquals("Steve", only(join, "world_join").get("player"));
        Snapshot hit = copy(s);
        hit.health = 0f;
        hit.dead = true;
        hit.deathScreen = true;
        hit.deathMessage = "Steve tried to swim in lava";
        hit.deathMessageKey = "death.attack.lava";
        Event d = only(tick(hit), "death");
        assertEquals("Steve tried to swim in lava", d.get("message"));

        tracker = new StateTracker(false, 0, 2f, DEBOUNCE);
        events.clear();
        states.clear();
        tickN(s, StateTracker.SETTLE_QUIET_TICKS + 1);
        tick(hit);
        for (Event e : events) assertFalse(e.toString().contains("Steve"), e.toString());
        for (Event e : states) assertFalse(e.toString().contains("Steve"), e.toString());
    }

    @Test
    void deadPlayerMovedToAnotherDimensionIsNotARespawn() {
        Snapshot s = joined();
        Snapshot dead = copy(s);
        dead.health = 0f;
        dead.dead = true;
        dead.deathScreen = true;
        dead.deathMessageKey = "death.attack.onFire";
        dead.deathMessage = "burned";
        dead.hurtTime = 10;
        tick(dead);
        Snapshot moved = copy(dead);
        moved.identity = 5;
        moved.dimension = "minecraft:the_nether";
        List<Event> out = tickN(moved, StateTracker.SETTLE_QUIET_TICKS + 5);
        assertFalse(names(out).contains("respawn"), out.toString());
        Snapshot alive = snap();
        alive.identity = 6;
        List<Event> out2 = tickN(alive, StateTracker.SETTLE_QUIET_TICKS + 2);
        assertTrue(names(out2).contains("respawn"), out2.toString());
        assertFalse(names(out2).contains("heal"), out2.toString());
    }

    @Test
    void deathWithoutScreenIsReportedAfterWait() {
        Snapshot s = joined();
        Snapshot dead = copy(s);
        dead.health = 0f;
        dead.dead = true;
        dead.hurtTime = 10;
        dead.belowWorld = true;
        tick(dead);
        List<Event> out = tickN(copy(dead), StateTracker.DEATH_WAIT_TICKS);
        Event d = only(out, "death");
        assertEquals("void", d.get("cause"));
        assertEquals(true, d.get("estimated"));
    }

    @Test
    void dimensionChangeDoesNotProduceFakeHealOrEffectEvents() {
        Snapshot s = joined();
        Snapshot hurt = copy(s);
        hurt.health = 9f;
        hurt.hurtTime = 10;
        hurt.effects.add(new Effect("minecraft:fire_resistance", 0, 3000, false, "beneficial"));
        tick(hurt);
        Snapshot portal = snap();
        portal.identity = 7;
        portal.dimension = "minecraft:the_nether";
        List<Event> out = tick(portal);
        Event dim = only(out, "dimension_change");
        assertEquals("minecraft:overworld", dim.get("from"));
        assertEquals("minecraft:the_nether", dim.get("to"));
        Snapshot settled = copy(hurt);
        settled.identity = 7;
        settled.dimension = "minecraft:the_nether";
        settled.hurtTime = 0;
        settled.effects.clear();
        settled.effects.add(new Effect("minecraft:fire_resistance", 0, 2990, false, "beneficial"));
        List<Event> after = tickN(settled, StateTracker.SETTLE_QUIET_TICKS + 5);
        assertTrue(after.isEmpty(), after.toString());
    }

    @Test
    void slowDimensionLoadWaitsForRealValues() {
        Snapshot s = joined();
        Snapshot hurt = copy(s);
        hurt.health = 16f;
        hurt.xpLevel = 5;
        hurt.hurtTime = 10;
        tick(hurt);
        events.clear();
        Snapshot loading = snap();
        loading.identity = 9;
        loading.dimension = "minecraft:the_nether";
        loading.screenOpen = true;
        assertEquals(names(tickN(loading, 60)), java.util.Arrays.asList("dimension_change"));
        Snapshot shown = copy(loading);
        shown.screenOpen = false;
        assertTrue(tickN(shown, 5).isEmpty());
        Snapshot real = copy(shown);
        real.health = 16f;
        real.xpLevel = 5;
        List<Event> out = tickN(real, 40);
        assertTrue(out.isEmpty(), out.toString());
    }

    @Test
    void togglesMountAndLevel() {
        Snapshot s = joined();
        Snapshot t = copy(s);
        t.sprinting = true;
        t.vehicle = "minecraft:horse";
        t.xpLevel = 3;
        t.fallFlying = true;
        List<String> n = names(tickN(t, DEBOUNCE + 1));
        assertTrue(n.containsAll(java.util.Arrays.asList("sprint_start", "mount", "level_up", "elytra_start")), n.toString());
        Snapshot u = copy(t);
        u.vehicle = null;
        u.fallFlying = false;
        u.sprinting = true;
        List<Event> out = tick(u);
        assertEquals("minecraft:horse", only(out, "dismount").get("vehicle"));
        assertTrue(names(out).contains("elytra_end"));
        Snapshot nul = copy(u);
        nul.fallFlying = null;
        nul.frozenTicks = null;
        assertTrue(tickN(nul, 5).isEmpty());
    }

    @Test
    void leaveAndRejoin() {
        Snapshot s = joined();
        Snapshot gone = new Snapshot();
        assertEquals("world_leave", only(tick(gone), "world_leave").name);
        assertTrue(tick(gone).isEmpty());
        joined();
    }

    @Test
    void stateLinesCarryCurrentValuesAndAreThrottled() {
        Snapshot s = joined();
        assertTrue(states.isEmpty(), "world_join already carries the state");
        tickN(copy(s), StateTracker.STATE_INTERVAL_TICKS);
        Snapshot a = copy(s);
        a.health = 15f;
        a.hurtTime = 10;
        tick(a);
        assertEquals(1, states.size());
        Event st = states.get(0);
        assertEquals(15f, st.get("health"));
        assertEquals(20f, st.get("max_health"));
        assertEquals(300, st.get("max_air"));
        assertEquals(20, st.get("max_food"));
        assertEquals(true, st.get("alive"));
        Snapshot b = copy(a);
        b.health = 14f;
        tick(b);
        Snapshot c = copy(b);
        c.health = 13f;
        tick(c);
        assertEquals(1, states.size());
        tickN(copy(c), StateTracker.STATE_INTERVAL_TICKS);
        assertEquals(2, states.size());
        assertEquals(13f, states.get(1).get("health"));
        tickN(copy(c), 50);
        assertEquals(2, states.size(), "no change, no line");
        assertEquals("state", Config.kindOf("state"));
        assertEquals("state", Config.kindOf("air"));
        assertEquals("event", Config.kindOf("hurt"));
        assertEquals("event", Config.kindOf("water_enter"));
    }

    @Test
    void toggleLinesSayOnOrOff() {
        Snapshot s = joined();
        Snapshot w = copy(s);
        w.onFire = true;
        assertEquals(true, only(tick(w), "fire_start").get("on"));
        Snapshot x = copy(s);
        assertTrue(tickN(x, 3).stream().anyMatch(e -> e.name.equals("fire_end") && Boolean.FALSE.equals(e.get("on"))));
    }

    @Test
    void naturalRegenerationIsMergedButPotionsAreImmediate() {
        tracker = new StateTracker(false, 20, 2f, DEBOUNCE);
        Snapshot s = joined();
        Snapshot hurt = copy(s);
        hurt.health = 10f;
        hurt.hurtTime = 10;
        tick(hurt);
        Snapshot cur = copy(hurt);
        cur.hurtTime = 0;
        List<Event> out = new ArrayList<Event>();
        for (int i = 0; i < 5; i++) {
            cur = copy(cur);
            cur.health += 0.17f;
            out.addAll(tick(cur));
        }
        assertFalse(names(out).contains("heal"), "small heals wait: " + out);
        List<Event> later = tickN(copy(cur), 20);
        Event h = only(later, "heal");
        assertEquals(5, h.get("count"));
        assertEquals(0.85f, (Float) h.get("amount"), 0.001f);
        assertEquals(cur.health, (Float) h.get("health"), 0.001f);
        Snapshot small = copy(cur);
        small.health += 0.5f;
        tick(small);
        Snapshot potion = copy(small);
        potion.health += 4f;
        List<Event> p = tick(potion);
        assertEquals(java.util.Arrays.asList("heal", "heal"), names(p));
        assertEquals(1, p.get(0).get("count"));
        assertEquals(4f, p.get(1).get("amount"));
        Snapshot s2 = copy(potion);
        s2.health += 0.2f;
        tick(s2);
        Snapshot hit = copy(s2);
        hit.health -= 3f;
        hit.hurtTime = 10;
        assertEquals(java.util.Arrays.asList("heal", "hurt"), names(tick(hit)));
    }

    @Test
    void surfaceSwimmingFlickerIsIgnoredButDrowningIsNot() {
        Snapshot s = joined();
        List<Event> out = new ArrayList<Event>();
        for (int i = 0; i < 40; i++) {
            Snapshot f = copy(s);
            boolean in = (i / 3) % 2 == 0;
            f.inWater = in;
            f.underWater = in;
            out.addAll(tick(f));
        }
        assertTrue(out.isEmpty(), out.toString());
        Snapshot under = copy(s);
        under.inWater = true;
        under.underWater = true;
        under.air = 0;
        List<String> first = names(tick(under));
        assertTrue(first.contains("drowning_start"), "danger is not delayed: " + first);
        assertFalse(first.contains("water_enter"));
        List<String> rest = names(tickN(under, DEBOUNCE + 1));
        assertTrue(rest.containsAll(java.util.Arrays.asList("water_enter", "submerged_start")), rest.toString());
        Config c = Config.parse("{\"heal_merge_ms\":0,\"toggle_debounce_ms\":100,\"heal_instant_min\":3}");
        assertEquals(0, StateTracker.msToTicks(c.healMergeMs));
        assertEquals(2, StateTracker.msToTicks(c.toggleDebounceMs));
        assertEquals(3.0, c.healInstantMin);
    }

    @Test
    void causeMapping() {
        assertEquals("fire", CauseMapper.fromId("minecraft:in_fire"));
        assertEquals("fire", CauseMapper.fromId("inFire"));
        assertEquals("void", CauseMapper.fromId("outOfWorld"));
        assertEquals("explosion", CauseMapper.fromId("explosion.player"));
        assertEquals("suffocation", CauseMapper.fromId("minecraft:in_wall"));
        assertEquals("freeze", CauseMapper.fromId("minecraft:freeze"));
        assertEquals("player", CauseMapper.fromDamage(null, "mob", "minecraft:player"));
        assertEquals("projectile", CauseMapper.fromDamage("minecraft:arrow", "arrow", "minecraft:skeleton"));
        assertEquals("mob", CauseMapper.fromDamage("mymod:custom", null, "minecraft:zombie"));
        assertEquals("unknown", CauseMapper.fromDamage("mymod:custom", null, null));
        assertEquals("fall", CauseMapper.fromDeathKey("death.fell.accident.ladder"));
        assertEquals("lava", CauseMapper.fromDeathKey("death.attack.lava.player"));
        assertEquals("explosion", CauseMapper.fromDeathKey("death.attack.badRespawnPoint.message"));
        assertEquals("drown", CauseMapper.fromDeathKey("death.attack.drown"));
        assertEquals("cactus", CauseMapper.fromDeathKey("death.attack.cactus.player"));
        assertEquals("starve", CauseMapper.fromDeathKey("death.attack.starve"));
        assertNull(CauseMapper.fromDeathKey("chat.type.text"));
    }
}

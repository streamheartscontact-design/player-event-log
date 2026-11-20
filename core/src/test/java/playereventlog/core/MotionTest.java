package playereventlog.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MotionTest {
    private StateTracker tracker;
    private final List<Event> events = new ArrayList<Event>();
    private final List<Event> states = new ArrayList<Event>();
    private double x;
    private double y = 64;

    @BeforeEach
    void setUp() {
        tracker = new StateTracker(false, 0, 2f, 8);
        events.clear();
        states.clear();
        x = 0;
        y = 64;
    }

    private Snapshot at() {
        Snapshot s = StateTrackerTest.snap();
        s.positionKnown = true;
        s.x = x;
        s.y = y;
        s.z = 5;
        s.onGround = true;
        s.jumpInput = false;
        s.attackKey = false;
        s.useKey = false;
        s.mining = false;
        return s;
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

    private List<Event> walk(double speed, int n, boolean sprint) {
        List<Event> out = new ArrayList<Event>();
        for (int i = 0; i < n; i++) {
            x += speed;
            Snapshot s = at();
            s.sprinting = sprint;
            out.addAll(tick(s));
        }
        return out;
    }

    private void join() {
        for (int i = 0; i <= StateTracker.SETTLE_QUIET_TICKS; i++) tick(at());
        assertEquals("world_join", events.get(events.size() - 1).name);
        events.clear();
    }

    private static List<String> names(List<Event> list) {
        List<String> n = new ArrayList<String>();
        for (Event e : list) n.add(e.name);
        return n;
    }

    private static int count(List<Event> list, String name) {
        int c = 0;
        for (Event e : list) if (e.name.equals(name)) c++;
        return c;
    }

    @Test
    void walkingStartsAndEndsAfterAQuarterSecond() {
        join();
        assertTrue(walk(0.2, 4, false).isEmpty(), "4 ticks are not enough");
        List<Event> start = walk(0.2, 1, false);
        assertEquals(java.util.Arrays.asList("move_start"), names(start));
        assertEquals("walk", start.get(0).get("pace"));
        assertEquals(Boolean.TRUE, start.get(0).get("on"));
        assertTrue(walk(0, 3, false).isEmpty());
        assertTrue(walk(0.2, 10, false).isEmpty());
        List<Event> end = walk(0, 6, false);
        assertEquals(java.util.Arrays.asList("move_end"), names(end));
        assertEquals(Boolean.FALSE, end.get(0).get("on"));
        assertNull(end.get(0).get("pace"));
        assertEquals("move_start", walk(0.065, 6, false).get(0).name);
        assertEquals("move_end", walk(0.02, 6, false).get(0).name);
    }

    @Test
    void runningSaysSprintAndJumpsKeepTheWalk() {
        join();
        List<Event> start = walk(0.28, 6, true);
        assertEquals("sprint", start.get(0).get("pace"));
        List<Event> air = new ArrayList<Event>();
        for (int i = 0; i < 12; i++) {
            x += 0.28;
            y += i == 0 ? 0.42 : (i < 6 ? 0.1 : -0.15);
            Snapshot s = at();
            s.sprinting = true;
            s.onGround = false;
            s.jumpInput = i == 0;
            air.addAll(tick(s));
        }
        air.removeIf(ev -> ev.name.startsWith("sprint_"));
        assertEquals(java.util.Arrays.asList("jump"), names(air));
        assertTrue(walk(0.28, 5, true).isEmpty(), "landed and still running");
        List<Event> fall = new ArrayList<Event>();
        for (int i = 0; i < StateTracker.MOVE_AIR_TICKS + 6; i++) {
            x += 0.1;
            y -= 0.5;
            Snapshot s = at();
            s.onGround = false;
            fall.addAll(tick(s));
        }
        assertEquals(1, count(fall, "move_end"));
    }

    @Test
    void teleportAndRidingAreNotWalking() {
        join();
        x += 40;
        assertTrue(walk(0, 10, false).isEmpty(), "teleport is no movement");
        walk(0.2, 6, false);
        List<Event> ride = new ArrayList<Event>();
        for (int i = 0; i < 6; i++) {
            x += 0.4;
            Snapshot s = at();
            s.vehicle = "minecraft:horse";
            s.onGround = false;
            ride.addAll(tick(s));
        }
        assertEquals(1, count(ride, "move_end"));
        assertEquals(0, count(ride, "move_start"));
    }

    @Test
    void jumpNeedsTheJumpInputAndIsLimited() {
        join();
        Snapshot up = at();
        y += 0.42;
        up = at();
        up.onGround = false;
        up.hurtTime = 10;
        assertTrue(tick(up).isEmpty());
        y = 64;
        tick(at());
        List<Event> out = new ArrayList<Event>();
        for (int i = 0; i < 2; i++) {
            y = 64.42;
            Snapshot j = at();
            j.onGround = false;
            j.jumpInput = true;
            out.addAll(tick(j));
            y = 64;
            out.addAll(tick(at()));
        }
        assertEquals(1, count(out, "jump"));
        for (int i = 0; i < 6; i++) tick(at());
        y = 64.42;
        Snapshot fallback = at();
        fallback.onGround = false;
        fallback.jumpInput = null;
        assertEquals(java.util.Arrays.asList("jump"), names(tick(fallback)));
        y = 64;
        tick(at());
        for (int i = 0; i < 6; i++) tick(at());
        y = 63.9;
        Snapshot ledge = at();
        ledge.onGround = false;
        ledge.jumpInput = true;
        assertTrue(tick(ledge).isEmpty());
    }

    @Test
    void swingsSayHandAndActionAndMiningHasItsOwnLines() {
        join();
        Snapshot attack = at();
        attack.swingStart = 1;
        attack.attackKey = true;
        Event e = tick(attack).get(0);
        assertEquals("swing", e.name);
        assertEquals("main", e.get("hand"));
        assertEquals("attack", e.get("action"));
        Snapshot again = at();
        again.swingStart = 1;
        again.attackKey = true;
        assertTrue(tick(again).isEmpty(), "at most 4 per second");
        for (int i = 0; i < 5; i++) tick(at());
        Snapshot off = at();
        off.swingStart = 2;
        e = tick(off).get(0);
        assertEquals("off", e.get("hand"));
        assertEquals("use", e.get("action"));
        for (int i = 0; i < 5; i++) tick(at());
        Snapshot place = at();
        place.swingStart = 1;
        place.useKey = true;
        assertEquals("use", tick(place).get(0).get("action"));
        for (int i = 0; i < 5; i++) tick(at());
        Snapshot held = at();
        held.useKey = true;
        tick(held);
        Snapshot quick = at();
        quick.swingStart = 1;
        assertEquals("use", tick(quick).get(0).get("action"));

        for (int i = 0; i < 5; i++) tick(at());
        List<Event> mine = new ArrayList<Event>();
        for (int i = 0; i < 20; i++) {
            Snapshot m = at();
            m.mining = true;
            m.attackKey = true;
            m.swingStart = i % 4 == 0 ? 1 : 0;
            mine.addAll(tick(m));
        }
        assertEquals(java.util.Arrays.asList("mine_start"), names(mine), "no swing lines while breaking");
        assertEquals(Boolean.TRUE, mine.get(0).get("on"));
        Snapshot pause = at();
        pause.attackKey = true;
        List<Event> stop = tick(pause);
        assertEquals(java.util.Arrays.asList("mine_end"), names(stop));
        assertEquals(Boolean.FALSE, stop.get(0).get("on"));
        for (int i = 0; i < 4; i++) {
            Snapshot p = at();
            p.attackKey = true;
            p.swingStart = i == 3 ? 1 : 0;
            assertTrue(tick(p).isEmpty());
        }
        for (int i = 0; i < StateTracker.MINE_GRACE_TICKS; i++) tick(at());
        Snapshot later = at();
        later.attackKey = true;
        later.swingStart = 1;
        assertEquals("swing", tick(later).get(0).name, "a punch well after mining is a swing again");
    }

    @Test
    void stateLineCarriesMovingSneakingAndOnGround() {
        join();
        walk(0.2, 6, false);
        for (int i = 0; i < StateTracker.STATE_INTERVAL_TICKS; i++) walk(0.2, 1, false);
        Event st = states.get(states.size() - 1);
        assertEquals(Boolean.TRUE, st.get("moving"));
        assertEquals(Boolean.FALSE, st.get("sneaking"));
        assertEquals(Boolean.TRUE, st.get("on_ground"));
        int before = states.size();
        for (int i = 0; i < StateTracker.STATE_INTERVAL_TICKS + 1; i++) {
            Snapshot s = at();
            s.sneaking = true;
            tick(s);
        }
        assertTrue(states.size() > before);
        assertEquals(Boolean.TRUE, states.get(states.size() - 1).get("sneaking"));
    }
}

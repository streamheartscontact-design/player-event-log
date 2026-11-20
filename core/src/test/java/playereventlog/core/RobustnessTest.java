package playereventlog.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RobustnessTest {
    static String longText(int n) {
        StringBuilder sb = new StringBuilder("mymod:");
        while (sb.length() < n) sb.append("very_long_name_");
        return sb.toString();
    }

    static final String[] ODD = {
            null, "", ":", "mymod:", ":thing", "mymod:Weird/Path.With-Chars_123", "MYMOD:UPPER",
            "quote\"and\\backslash", "line\nbreak\ttab\u0000nul", "éü日本😀", longText(5000)
    };

    @Test
    void causeMappingAcceptsAnything() {
        for (String id : ODD) {
            String c = CauseMapper.fromId(id);
            assertTrue(c == null || !c.isEmpty());
            assertEquals("unknown", CauseMapper.fromDamage(id, id, null), String.valueOf(id));
            assertEquals("mob", CauseMapper.fromDamage(id, null, "mymod:boss"));
            assertEquals("player", CauseMapper.fromDamage(id, null, "minecraft:player"));
            CauseMapper.fromDeathKey(id);
        }
        assertEquals("unknown", CauseMapper.fromDeathKey("death.attack."));
        assertEquals("unknown", CauseMapper.fromDeathKey("death.attack.mymod.custom_damage"));
        assertNull(CauseMapper.fromDeathKey("mymod.death.custom"));
    }

    @Test
    void oddEffectsAndDamageStillGiveValidLines() {
        StateTracker tracker = new StateTracker(false, 0, 2f, 8);
        final List<Event> out = new ArrayList<Event>();
        EventSink sink = new EventSink() {
            @Override
            public void emit(Event e) {
                out.add(e);
            }
        };
        Snapshot base = StateTrackerTest.snap();
        for (int i = 0; i <= StateTracker.SETTLE_QUIET_TICKS; i++) tracker.update(base, sink);

        Snapshot s = StateTrackerTest.copy(base);
        for (String id : ODD) s.effects.add(new Effect(id, 3, 200, false, id));
        s.health = 15f;
        s.hurtTime = 10;
        s.damageSupported = true;
        s.damage = new DamageInfo(longText(3000), "line\nbreak", "mymod:Strange Boss \"X\"", null, "", new Object());
        tracker.update(s, sink);
        for (int i = 0; i < 20; i++) tracker.update(StateTrackerTest.copy(s), sink);
        Snapshot gone = StateTrackerTest.copy(s);
        gone.effects = new ArrayList<Effect>();
        tracker.update(gone, sink);

        int adds = 0;
        int removes = 0;
        boolean hurt = false;
        for (Event e : out) {
            String line = Json.toJson(e.fields);
            Object parsed = MiniJson.parse(line);
            assertTrue(parsed instanceof Map, line);
            if (e.name.equals("effect_add")) adds++;
            if (e.name.equals("effect_remove")) removes++;
            if (e.name.equals("hurt")) {
                hurt = true;
                assertEquals("mob", e.get("cause"));
                assertEquals("mymod:Strange Boss \"X\"", ((Map<?, ?>) parsed).get("attacker"));
            }
        }
        assertEquals(ODD.length - 1, adds, "every effect with an id is reported, the one without id is skipped");
        assertEquals(ODD.length - 1, removes);
        assertTrue(hurt);
    }

    @Test
    void controlCharactersAreEscaped() {
        String line = Json.toJson("a\u0000b\u001fc d\"e\\f\ng");
        assertFalse(line.contains("\n"));
        assertEquals("a\u0000b\u001fc d\"e\\f\ng", MiniJson.parse(line));
    }
}

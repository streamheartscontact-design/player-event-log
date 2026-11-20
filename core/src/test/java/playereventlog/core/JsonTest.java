package playereventlog.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void numbersKeepADecimalPointAndRound() {
        assertEquals("14.0", Json.number(14f));
        assertEquals("2.5", Json.number(2.5));
        assertEquals("0.3", Json.number(0.1 + 0.2));
        assertEquals("1.23", Json.number(1.23456));
        assertEquals("0.0", Json.number(-0.001));
        assertEquals("null", Json.number(Double.NaN));
        assertEquals("12345678.0", Json.number(12345678.0));
    }

    @Test
    void stringsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", Json.toJson("a\"b\\c\nd\u0001"));
        assertEquals("\"\uD55C\uAE00 \uBA54\uC2DC\uC9C0\"", Json.toJson("\uD55C\uAE00 \uBA54\uC2DC\uC9C0"));
    }

    @Test
    void eventLineHasCommonHeaderAndRoundTrips() throws Exception {
        File dir = Files.createTempDirectory("pel-json").toFile();
        PlayerEventLog.resetForTests();
        PlayerEventLog log = PlayerEventLog.init(dir, "1.21.11", "fabric");
        Event e = new Event("hurt").put("amount", 2.0f).put("health", 14.0f).put("cause", "fall")
                .put("attacker", null).put("estimated", false);
        String line = log.format(e, new Date(0));
        assertTrue(line.startsWith("{\"v\":1,\"t\":\""), line);
        assertTrue(line.contains(",\"mc\":\"1.21.11\",\"loader\":\"fabric\",\"event\":\"hurt\",\"kind\":\"event\",\"amount\":2.0,\"health\":14.0,\"cause\":\"fall\",\"attacker\":null,\"estimated\":false}"), line);
        Map<?, ?> parsed = (Map<?, ?>) MiniJson.parse(line);
        assertEquals("hurt", parsed.get("event"));
        assertEquals(14.0, parsed.get("health"));
        assertNull(parsed.get("attacker"));
        assertTrue(((String) parsed.get("t")).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}([+-]\\d\\d:\\d\\d|Z)"));

        log.writerForTests().flush(3000);
        List<String> lines = Files.readAllLines(new File(dir, "logs/player-events.jsonl").toPath(), Charset.forName("UTF-8"));
        assertFalse(lines.isEmpty());
        Map<?, ?> first = (Map<?, ?>) MiniJson.parse(lines.get(0));
        assertEquals("session_start", first.get("event"));
        assertEquals("playereventlog", first.get("mod"));
        assertTrue(new File(dir, "config/player-events.json").isFile());
        PlayerEventLog.resetForTests();
    }

    @Test
    void miniJsonParsesNestedValues() {
        Object o = MiniJson.parse("{\"a\":[1,2.5,true,null,\"x\\u0041\"],\"b\":{\"c\":-3e2}}");
        Map<?, ?> m = (Map<?, ?>) o;
        assertEquals(Arrays.asList(1.0, 2.5, true, null, "xA"), m.get("a"));
        assertEquals(-300.0, ((Map<?, ?>) m.get("b")).get("c"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{} x"));
    }

    @Test
    void configParsesAndRoundTrips() {
        Config c = Config.parse("{\"enabled\":true,\"path\":\"custom/out.jsonl\",\"max_file_mb\":2,"
                + "\"include_names\":true,\"events\":{\"hurt\":false,\"sneak_start\":true}}");
        assertFalse(c.isEnabled("hurt"));
        assertTrue(c.isEnabled("sneak_start"));
        assertTrue(c.isEnabled("heal"));
        assertTrue(c.includeNames);
        assertEquals(2 * 1024 * 1024, c.maxBytes());
        assertEquals(new File(new File("g"), "custom/out.jsonl"), c.resolveLogFile(new File("g")));
        Config back = Config.parse(c.toJson());
        assertFalse(back.isEnabled("hurt"));
        assertEquals("custom/out.jsonl", back.path);

        Config d = new Config();
        assertTrue(d.isEnabled("sneak_start"), "sneak is on by default since 1.3.0");
        assertTrue(d.isEnabled("move_start"));
        assertEquals(Config.CONFIG_VERSION, d.configVersion);
        for (String ev : Config.EVENTS) assertTrue(d.events.containsKey(ev));
        Config off = Config.parse("{\"enabled\":false}");
        assertFalse(off.isEnabled("hurt"));
    }

    @Test
    void oldConfigIsMigratedOnce() throws Exception {
        File dir = Files.createTempDirectory("pel-cfg110").toFile();
        File f = new File(dir, Config.FILE_NAME);
        String old = "{\n  \"enabled\": true,\n  \"path\": \"logs/player-events.jsonl\",\n  \"include_names\": true,\n"
                + "  \"events\": {\n    \"hurt\": false,\n    \"sneak_start\": false,\n    \"sneak_end\": false\n  },\n"
                + "  \"old_setting\": {\n    \"enabled\": true\n  }\n}\n";
        Files.write(f.toPath(), old.getBytes(Charset.forName("UTF-8")));
        Config c = Config.load(dir);
        assertTrue(c.enabled);
        assertTrue(c.includeNames);
        assertFalse(c.isEnabled("hurt"), "own choices are kept");
        assertTrue(c.isEnabled("heal"));
        assertTrue(c.isEnabled("sneak_start"), "old default sneak off is turned on");
        assertTrue(c.isEnabled("jump"), "new events are on");
        String written = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
        assertTrue(written.contains("\"config_version\": 2"), written);
        assertFalse(written.contains("old_setting"), written);
        assertTrue(MiniJson.parse(written) instanceof Map);
        Config.load(dir);
        assertEquals(written, new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8")), "migrated only once");

        String now = "{\"config_version\": 2, \"events\": {\"sneak_start\": false, \"sneak_end\": false}}";
        Files.write(f.toPath(), now.getBytes(Charset.forName("UTF-8")));
        assertFalse(Config.load(dir).isEnabled("sneak_start"));
        assertEquals(now, new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8")));

        File fresh = Files.createTempDirectory("pel-cfg130").toFile();
        Config.load(fresh);
        String created = new String(Files.readAllBytes(new File(fresh, Config.FILE_NAME).toPath()), Charset.forName("UTF-8"));
        assertTrue(created.contains("\"config_version\": 2"), created);
        assertTrue(created.contains("\"move_start\": true"), created);
        assertTrue(created.contains("\"sneak_start\": true"), created);
    }

    @Test
    void writerRotatesWhenFileGetsLarge() throws Exception {
        File dir = Files.createTempDirectory("pel-rot").toFile();
        File f = new File(dir, "logs/x.jsonl");
        EventWriter w = new EventWriter(f, 4096);
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < 100; i++) pad.append('x');
        for (int i = 0; i < 100; i++) w.offer("{\"i\":" + i + ",\"p\":\"" + pad + "\"}");
        w.flush(3000);
        w.close(2000);
        File old = new File(f.getPath() + ".1");
        assertTrue(old.isFile(), "rotated file exists");
        assertTrue(f.length() <= 4096, "current file is under the limit");
        assertTrue(old.length() <= 4096, "rotated file is under the limit");
        List<String> cur = Files.readAllLines(f.toPath(), Charset.forName("UTF-8"));
        assertTrue(cur.get(cur.size() - 1).startsWith("{\"i\":99,"));
        for (String line : cur) MiniJson.parse(line);
    }
}

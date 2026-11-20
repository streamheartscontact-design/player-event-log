package playereventlog.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Config {
    public static final String FILE_NAME = "player-events.json";
    public static final int CONFIG_VERSION = 2;

    public static final List<String> EVENTS = Collections.unmodifiableList(Arrays.asList(
            "session_start", "session_end", "world_join", "world_leave", "state",
            "hurt", "heal", "death", "respawn", "death_screen_open", "death_screen_close",
            "max_health", "absorption", "food", "armor", "air",
            "water_enter", "water_leave", "submerged_start", "submerged_end", "drowning_start", "drowning_end",
            "lava_enter", "lava_leave", "fire_start", "fire_end",
            "freeze_start", "freeze_end", "frozen_start", "frozen_end",
            "effect_add", "effect_change", "effect_remove",
            "level_up", "level_down", "dimension_change", "gamemode_change",
            "sleep_start", "sleep_end", "elytra_start", "elytra_end", "mount", "dismount",
            "swim_start", "swim_end", "sneak_start", "sneak_end", "sprint_start", "sprint_end",
            "move_start", "move_end", "jump", "swing", "mine_start", "mine_end"));

    public static final java.util.Set<String> STATE_EVENTS = Collections.unmodifiableSet(new java.util.HashSet<String>(
            Arrays.asList("world_join", "state", "max_health", "absorption", "food", "armor", "air")));

    public static String kindOf(String event) {
        return STATE_EVENTS.contains(event) ? "state" : "event";
    }

    public int configVersion = CONFIG_VERSION;
    public boolean enabled = true;
    public String path = "logs/player-events.jsonl";
    public double maxFileMb = 10.0;
    public boolean includeNames = false;
    public double healMergeMs = 1000;
    public double healInstantMin = 2.0;
    public double toggleDebounceMs = 400;
    public final Map<String, Boolean> events = new LinkedHashMap<String, Boolean>();

    public Config() {
        for (String e : EVENTS) events.put(e, Boolean.TRUE);
    }

    public boolean isEnabled(String event) {
        if (!enabled) return false;
        Boolean b = events.get(event);
        return b == null || b.booleanValue();
    }

    public long maxBytes() {
        return (long) (Math.max(0.01, maxFileMb) * 1024 * 1024);
    }

    public File resolveLogFile(File gameDir) {
        File f = new File(path);
        return f.isAbsolute() ? f : new File(gameDir, path);
    }

    @SuppressWarnings("unchecked")
    public static Config parse(String text) {
        Config c = new Config();
        Object root = MiniJson.parse(text);
        if (!(root instanceof Map)) throw new IllegalArgumentException("config root must be an object");
        Map<String, Object> m = (Map<String, Object>) root;
        c.configVersion = m.get("config_version") instanceof Double ? ((Double) m.get("config_version")).intValue() : 1;
        if (m.get("enabled") instanceof Boolean) c.enabled = (Boolean) m.get("enabled");
        if (m.get("path") instanceof String && !((String) m.get("path")).trim().isEmpty()) c.path = (String) m.get("path");
        if (m.get("max_file_mb") instanceof Double) c.maxFileMb = (Double) m.get("max_file_mb");
        if (m.get("include_names") instanceof Boolean) c.includeNames = (Boolean) m.get("include_names");
        if (m.get("heal_merge_ms") instanceof Double) c.healMergeMs = (Double) m.get("heal_merge_ms");
        if (m.get("heal_instant_min") instanceof Double) c.healInstantMin = (Double) m.get("heal_instant_min");
        if (m.get("toggle_debounce_ms") instanceof Double) c.toggleDebounceMs = (Double) m.get("toggle_debounce_ms");
        if (m.get("events") instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("events")).entrySet()) {
                if (e.getValue() instanceof Boolean) c.events.put(e.getKey(), (Boolean) e.getValue());
            }
        }
        return c;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"config_version\": ").append(configVersion).append(",\n");
        sb.append("  \"enabled\": ").append(enabled).append(",\n");
        sb.append("  \"path\": ");
        Json.quote(sb, path);
        sb.append(",\n");
        sb.append("  \"max_file_mb\": ").append(Json.number(maxFileMb)).append(",\n");
        sb.append("  \"include_names\": ").append(includeNames).append(",\n");
        sb.append("  \"heal_merge_ms\": ").append(Json.number(healMergeMs)).append(",\n");
        sb.append("  \"heal_instant_min\": ").append(Json.number(healInstantMin)).append(",\n");
        sb.append("  \"toggle_debounce_ms\": ").append(Json.number(toggleDebounceMs)).append(",\n");
        sb.append("  \"events\": {\n");
        int i = 0;
        for (Map.Entry<String, Boolean> e : events.entrySet()) {
            sb.append("    ");
            Json.quote(sb, e.getKey());
            sb.append(": ").append(e.getValue());
            sb.append(++i < events.size() ? ",\n" : "\n");
        }
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static Config load(File configDir) {
        File f = new File(configDir, FILE_NAME);
        Charset utf8 = Charset.forName("UTF-8");
        if (f.isFile()) {
            Config c;
            try {
                c = parse(new String(Files.readAllBytes(f.toPath()), utf8));
            } catch (Exception e) {
                System.err.println("[PlayerEventLog] " + f + " could not be read, using defaults: " + e.getMessage());
                return new Config();
            }
            if (c.migrate()) save(c, f);
            return c;
        }
        Config c = new Config();
        save(c, f);
        return c;
    }

    boolean migrate() {
        if (configVersion >= CONFIG_VERSION) return false;
        if (Boolean.FALSE.equals(events.get("sneak_start")) && Boolean.FALSE.equals(events.get("sneak_end"))) {
            events.put("sneak_start", Boolean.TRUE);
            events.put("sneak_end", Boolean.TRUE);
        }
        configVersion = CONFIG_VERSION;
        return true;
    }

    private static void save(Config c, File f) {
        Charset utf8 = Charset.forName("UTF-8");
        File configDir = f.getParentFile();
        try {
            if (!configDir.isDirectory()) configDir.mkdirs();
            Writer w = new OutputStreamWriter(new FileOutputStream(f), utf8);
            try {
                w.write(c.toJson());
            } finally {
                w.close();
            }
        } catch (IOException e) {
            System.err.println("[PlayerEventLog] could not write config: " + e.getMessage());
        }
    }
}

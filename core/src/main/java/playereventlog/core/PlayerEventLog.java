package playereventlog.core;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public final class PlayerEventLog {
    private static volatile PlayerEventLog instance;

    private final Config config;
    private final EventWriter writer;
    private final StateTracker tracker;
    private final String headerPrefix;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private final EventSink sink = new EventSink() {
        @Override
        public void emit(Event event) {
            write(event);
        }
    };
    private int errors;

    private PlayerEventLog(File gameDir, String mcVersion, String loader) {
        this.config = Config.load(new File(gameDir, "config"));
        this.writer = config.enabled ? new EventWriter(config.resolveLogFile(gameDir), config.maxBytes()) : null;
        this.tracker = new StateTracker(config);
        StringBuilder sb = new StringBuilder();
        sb.append(",\"mc\":");
        Json.quote(sb, mcVersion == null ? "unknown" : mcVersion);
        sb.append(",\"loader\":");
        Json.quote(sb, loader);
        this.headerPrefix = sb.toString();
    }

    public static synchronized PlayerEventLog init(File gameDir, String mcVersion, String loader) {
        if (instance != null) return instance;
        final PlayerEventLog log = new PlayerEventLog(gameDir, mcVersion, loader);
        instance = log;
        log.write(new Event("session_start")
                .put("mod", ModInfo.ID)
                .put("mod_version", ModInfo.VERSION)
                .put("schema", ModInfo.SCHEMA)
                .put("session", UUID.randomUUID().toString())
                .put("java", System.getProperty("java.version")));
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                log.shutdown();
            }
        }, "PlayerEventLog-shutdown"));
        if (log.writer != null) {
            System.out.println("[PlayerEventLog] writing player events to " + log.writer.file().getAbsolutePath());
        }
        return log;
    }

    public static PlayerEventLog get() {
        return instance;
    }

    public Config config() {
        return config;
    }

    public void tick(Snapshot snapshot) {
        if (!wantsSnapshots()) return;
        try {
            tracker.update(snapshot, sink);
        } catch (Throwable t) {
            if (++errors <= 3 || errors == 50) {
                System.err.println("[PlayerEventLog] tick failed (" + errors + "): " + t);
                t.printStackTrace();
            }
        }
    }

    public void reportError(Throwable t) {
        if (++errors <= 3 || errors == 50) {
            System.err.println("[PlayerEventLog] adapter failed (" + errors + "): " + t);
            t.printStackTrace();
        }
    }

    public boolean disabled() {
        return !wantsSnapshots();
    }

    public boolean wantsSnapshots() {
        return writer != null && config.enabled && errors <= 50;
    }

    void write(Event e) {
        if (writer == null || !config.isEnabled(e.name)) return;
        writer.offer(format(e, new Date()));
    }

    String format(Event e, Date when) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"v\":").append(ModInfo.SCHEMA).append(",\"t\":");
        String t;
        synchronized (timeFormat) {
            t = timeFormat.format(when);
        }
        Json.quote(sb, t);
        sb.append(headerPrefix).append(",\"event\":");
        Json.quote(sb, e.name);
        sb.append(",\"kind\":");
        Json.quote(sb, Config.kindOf(e.name));
        for (java.util.Map.Entry<String, Object> f : e.fields.entrySet()) {
            sb.append(',');
            Json.quote(sb, f.getKey());
            sb.append(':');
            Json.write(sb, f.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private void shutdown() {
        if (writer == null) return;
        write(new Event("session_end").put("dropped", writer.dropped()));
        writer.close(2000);
    }

    static synchronized void resetForTests() {
        if (instance != null && instance.writer != null) instance.writer.close(2000);
        instance = null;
    }

    EventWriter writerForTests() {
        return writer;
    }
}

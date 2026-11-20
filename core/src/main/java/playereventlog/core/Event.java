package playereventlog.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Event {
    public final String name;
    public final Map<String, Object> fields = new LinkedHashMap<String, Object>();

    public Event(String name) {
        this.name = name;
    }

    public Event put(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    public Object get(String key) {
        return fields.get(key);
    }

    @Override
    public String toString() {
        return name + Json.toJson(fields);
    }
}

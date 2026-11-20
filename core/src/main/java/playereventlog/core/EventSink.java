package playereventlog.core;

public interface EventSink {
    void emit(Event event);
}

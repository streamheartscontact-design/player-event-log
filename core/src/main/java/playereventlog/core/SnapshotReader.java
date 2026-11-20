package playereventlog.core;

public interface SnapshotReader {
    Snapshot read(Object game);
}

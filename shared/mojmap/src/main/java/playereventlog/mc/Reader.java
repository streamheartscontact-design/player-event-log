package playereventlog.mc;

import net.minecraft.client.Minecraft;
import playereventlog.core.Snapshot;
import playereventlog.core.SnapshotReader;

public final class Reader implements SnapshotReader {
    public Reader() {
    }

    @Override
    public Snapshot read(Object game) {
        return MojmapReader.read((Minecraft) game);
    }
}

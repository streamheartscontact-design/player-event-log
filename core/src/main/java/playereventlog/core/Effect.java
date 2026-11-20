package playereventlog.core;

public final class Effect {
    public final String id;
    public final int amplifier;
    public final int duration;
    public final boolean ambient;
    public final String category;

    public Effect(String id, int amplifier, int duration, boolean ambient, String category) {
        this.id = id;
        this.amplifier = amplifier;
        this.duration = duration;
        this.ambient = ambient;
        this.category = category;
    }
}

package dev.liamtolkkinen.sanctuary.effect;

public enum AnchorEffect {
    REGENERATION(Target.SAFE, 2),
    RESISTANCE(Target.SAFE, 2),
    STRENGTH(Target.SAFE, 2),
    HASTE(Target.SAFE, 2),
    SPEED(Target.SAFE, 3),
    NIGHT_VISION(Target.SAFE, 1),
    DOLPHINS_GRACE(Target.SAFE, 1),

    ELYTRA_DISABLED(Target.HOSTILE, 1),
    MINING_FATIGUE(Target.HOSTILE, 3),
    WEAKNESS(Target.HOSTILE, 3),
    BLINDNESS(Target.HOSTILE, 1),
    WITHER(Target.HOSTILE, 2),
    SLOWNESS(Target.HOSTILE, 3);

    private final Target target;
    private final int maximumLevel;

    AnchorEffect(Target target, int maximumLevel) {
        this.target = target;
        this.maximumLevel = maximumLevel;
    }

    public Target target() {
        return target;
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    public boolean hasAmplifier() {
        return maximumLevel > 1;
    }

    public enum Target {
        SAFE,
        HOSTILE
    }
}

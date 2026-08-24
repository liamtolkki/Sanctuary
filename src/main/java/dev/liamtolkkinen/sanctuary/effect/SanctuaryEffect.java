package dev.liamtolkkinen.sanctuary.effect;

public enum SanctuaryEffect {
    REGENERATION(1, EffectTarget.SAFE, 2),
    RESISTANCE(2, EffectTarget.SAFE, 2),
    STRENGTH(3, EffectTarget.SAFE, 2),
    HASTE(4, EffectTarget.SAFE, 2),
    SPEED(5, EffectTarget.SAFE, 3),
    NIGHT_VISION(2, EffectTarget.SAFE, 1),
    DOLPHINS_GRACE(4, EffectTarget.SAFE, 1),

    ELYTRA_DISABLED(5, EffectTarget.HOSTILE, 1),
    MINING_FATIGUE(4, EffectTarget.HOSTILE, 3),
    WEAKNESS(3, EffectTarget.HOSTILE, 3),
    BLINDNESS(2, EffectTarget.HOSTILE, 1),
    WITHER(1, EffectTarget.HOSTILE, 2),
    SLOWNESS(4, EffectTarget.HOSTILE, 3);

    private final int tier;
    private final EffectTarget target;
    private final int maximumLevel;

    SanctuaryEffect(int tier, EffectTarget target, int maximumLevel) {
        this.tier = tier;
        this.target = target;
        this.maximumLevel = maximumLevel;
    }

    /**
     * Legacy/default Beacon tier. Anchor-specific catalogs may unlock the same
     * effect at a different tier.
     */
    public int tier() {
        return tier;
    }

    public EffectTarget target() {
        return target;
    }

    public int maximumLevel() {
        return maximumLevel;
    }

    public boolean hasAmplifier() {
        return maximumLevel > 1;
    }

    public enum EffectTarget {
        SAFE,
        HOSTILE
    }
}

package dev.liamtolkkinen.sanctuary.effect;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.security.SanctuaryThreat;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SanctuaryEffectService {
    public static final int EFFECT_TIER_COUNT = 5;

    private final SanctuaryEffectRepository repository;
    private final SanctuarySecurityService securityService;

    public SanctuaryEffectService(
        SanctuaryEffectRepository repository,
        SanctuarySecurityService securityService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.securityService = Objects.requireNonNull(securityService, "securityService");
    }

    public double segmentDelta(double maximumRadius) {
        validateMaximumRadius(maximumRadius);
        return maximumRadius / EFFECT_TIER_COUNT;
    }

    public double radiusForTier(double maximumRadius, int effectTier) {
        if (effectTier < 1 || effectTier > EFFECT_TIER_COUNT) {
            throw new IllegalArgumentException("effectTier must be between 1 and 5");
        }
        return segmentDelta(maximumRadius) * effectTier;
    }

    public boolean isUnlocked(Sanctuary sanctuary, SanctuaryEffect effect) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(effect, "effect");
        return sanctuary.tier() >= effect.tier();
    }

    public boolean isWithinEffectRadius(
        Sanctuary sanctuary,
        SanctuaryEffect effect,
        double horizontalDistance,
        double maximumRadius
    ) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(effect, "effect");
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0.0) {
            throw new IllegalArgumentException("horizontalDistance must be finite and zero or greater");
        }
        if (!isUnlocked(sanctuary, effect)) {
            return false;
        }
        double effectiveRadius = Math.min(
            sanctuary.territoryRadius(),
            radiusForTier(maximumRadius, effect.tier())
        );
        return horizontalDistance <= effectiveRadius;
    }

    public int level(Sanctuary sanctuary, SanctuaryEffect effect) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(effect, "effect");
        int level = repository.getLevel(sanctuary.id(), effect);
        if (level < 1) {
            return 1;
        }
        return Math.min(level, effect.maximumLevel());
    }

    public void setLevel(Sanctuary sanctuary, SanctuaryEffect effect, int level) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(effect, "effect");
        if (!isUnlocked(sanctuary, effect)) {
            throw new IllegalStateException("That effect is not unlocked at this Beacon tier.");
        }
        repository.setLevel(sanctuary.id(), effect, level);
    }

    public List<ActiveSanctuaryEffect> activeEffects(
        Sanctuary sanctuary,
        UUID playerId,
        double horizontalDistance,
        double maximumRadius
    ) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        SanctuaryThreat threat = securityService.threat(sanctuary, playerId);
        SanctuaryEffect.EffectTarget target = switch (threat) {
            case SAFE -> SanctuaryEffect.EffectTarget.SAFE;
            case HOSTILE -> SanctuaryEffect.EffectTarget.HOSTILE;
            case NEUTRAL -> null;
        };
        if (target == null) {
            return List.of();
        }

        List<ActiveSanctuaryEffect> active = new ArrayList<>();
        for (SanctuaryEffect effect : SanctuaryEffect.values()) {
            if (effect.target() != target) {
                continue;
            }
            if (!isWithinEffectRadius(sanctuary, effect, horizontalDistance, maximumRadius)) {
                continue;
            }
            active.add(new ActiveSanctuaryEffect(effect, level(sanctuary, effect)));
        }
        return List.copyOf(active);
    }

    private static void validateMaximumRadius(double maximumRadius) {
        if (!Double.isFinite(maximumRadius) || maximumRadius <= 0.0) {
            throw new IllegalArgumentException("maximumRadius must be finite and greater than zero");
        }
    }

    public record ActiveSanctuaryEffect(SanctuaryEffect effect, int level) {
        public ActiveSanctuaryEffect {
            Objects.requireNonNull(effect, "effect");
            if (level < 1 || level > effect.maximumLevel()) {
                throw new IllegalArgumentException("level is outside the effect maximum");
            }
        }

        public int amplifier() {
            return level - 1;
        }
    }
}

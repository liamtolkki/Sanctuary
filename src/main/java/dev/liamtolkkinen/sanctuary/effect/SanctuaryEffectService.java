package dev.liamtolkkinen.sanctuary.effect;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.security.SanctuaryThreat;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SanctuaryEffectService {
    public static final int EFFECT_TIER_COUNT = 5;

    private static final List<AnchorEffectDefinition> BEACON_SAFE = List.of(
        definition(SanctuaryEffect.REGENERATION, 1),
        definition(SanctuaryEffect.RESISTANCE, 2),
        definition(SanctuaryEffect.STRENGTH, 3),
        definition(SanctuaryEffect.HASTE, 4),
        definition(SanctuaryEffect.SPEED, 5)
    );
    private static final List<AnchorEffectDefinition> BEACON_HOSTILE = List.of(
        definition(SanctuaryEffect.WITHER, 1),
        definition(SanctuaryEffect.BLINDNESS, 2),
        definition(SanctuaryEffect.WEAKNESS, 3),
        definition(SanctuaryEffect.MINING_FATIGUE, 4),
        definition(SanctuaryEffect.ELYTRA_DISABLED, 5)
    );
    private static final List<AnchorEffectDefinition> CONDUIT_SAFE = List.of(
        definition(SanctuaryEffect.REGENERATION, 1),
        definition(SanctuaryEffect.NIGHT_VISION, 2),
        definition(SanctuaryEffect.HASTE, 3),
        definition(SanctuaryEffect.DOLPHINS_GRACE, 4),
        definition(SanctuaryEffect.RESISTANCE, 5)
    );
    private static final List<AnchorEffectDefinition> CONDUIT_HOSTILE = List.of(
        definition(SanctuaryEffect.WITHER, 1),
        definition(SanctuaryEffect.BLINDNESS, 2),
        definition(SanctuaryEffect.MINING_FATIGUE, 3),
        definition(SanctuaryEffect.SLOWNESS, 4),
        definition(SanctuaryEffect.WEAKNESS, 5)
    );

    private final SanctuaryEffectRepository legacyRepository;
    private final AnchorEffectRepository anchorRepository;
    private final SanctuarySecurityService securityService;

    public SanctuaryEffectService(
        SanctuaryEffectRepository repository,
        SanctuarySecurityService securityService
    ) {
        this(repository, null, securityService);
    }

    public SanctuaryEffectService(
        SanctuaryEffectRepository legacyRepository,
        AnchorEffectRepository anchorRepository,
        SanctuarySecurityService securityService
    ) {
        this.legacyRepository = Objects.requireNonNull(legacyRepository, "legacyRepository");
        this.anchorRepository = anchorRepository;
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

    public List<AnchorEffectDefinition> definitions(
        SanctuaryType type,
        SanctuaryEffect.EffectTarget target
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        return switch (type) {
            case BEACON -> target == SanctuaryEffect.EffectTarget.SAFE ? BEACON_SAFE : BEACON_HOSTILE;
            case CONDUIT -> target == SanctuaryEffect.EffectTarget.SAFE ? CONDUIT_SAFE : CONDUIT_HOSTILE;
        };
    }

    public int tierFor(SanctuaryType type, SanctuaryEffect effect) {
        return definitions(type, effect.target()).stream()
            .filter(definition -> definition.effect() == effect)
            .mapToInt(AnchorEffectDefinition::tier)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                effect.name() + " is not available for " + type.name().toLowerCase(java.util.Locale.ROOT) + " anchors"
            ));
    }

    public boolean isUnlocked(SanctuaryAnchor anchor, SanctuaryEffect effect) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(effect, "effect");
        try {
            return anchor.tier() >= tierFor(anchor.type(), effect);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isWithinEffectRadius(
        SanctuaryAnchor anchor,
        SanctuaryEffect effect,
        double horizontalDistance,
        double maximumRadius
    ) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(effect, "effect");
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0.0) {
            throw new IllegalArgumentException("horizontalDistance must be finite and zero or greater");
        }
        if (!isUnlocked(anchor, effect)) {
            return false;
        }
        double effectiveRadius = Math.min(
            anchor.territoryRadius(),
            radiusForTier(maximumRadius, tierFor(anchor.type(), effect))
        );
        return horizontalDistance <= effectiveRadius;
    }

    public int level(SanctuaryAnchor anchor, SanctuaryEffect effect) throws SQLException {
        requireAnchorRepository();
        int level = anchorRepository.getLevel(anchor.id(), effect);
        if (level < 1) {
            return 1;
        }
        return Math.min(level, effect.maximumLevel());
    }

    public void setLevel(SanctuaryAnchor anchor, SanctuaryEffect effect, int level) throws SQLException {
        requireAnchorRepository();
        if (!isUnlocked(anchor, effect)) {
            throw new IllegalStateException("That effect is not unlocked at this anchor tier.");
        }
        anchorRepository.setLevel(anchor.id(), effect, level);
    }

    public List<ActiveSanctuaryEffect> activeEffects(
        Sanctuary sanctuary,
        SanctuaryAnchor anchor,
        UUID playerId,
        double horizontalDistance,
        double maximumRadius
    ) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(playerId, "playerId");
        SanctuaryEffect.EffectTarget target = targetFor(sanctuary, playerId);
        if (target == null) {
            return List.of();
        }

        List<ActiveSanctuaryEffect> active = new ArrayList<>();
        for (AnchorEffectDefinition definition : definitions(anchor.type(), target)) {
            SanctuaryEffect effect = definition.effect();
            if (!isWithinEffectRadius(anchor, effect, horizontalDistance, maximumRadius)) {
                continue;
            }
            active.add(new ActiveSanctuaryEffect(effect, level(anchor, effect)));
        }
        return List.copyOf(active);
    }

    // Legacy single-anchor API kept for existing callers/tests until every UI path is anchor-aware.
    public boolean isUnlocked(Sanctuary sanctuary, SanctuaryEffect effect) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(effect, "effect");
        try {
            return sanctuary.tier() >= tierFor(SanctuaryType.BEACON, effect);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isWithinEffectRadius(
        Sanctuary sanctuary,
        SanctuaryEffect effect,
        double horizontalDistance,
        double maximumRadius
    ) {
        if (!isUnlocked(sanctuary, effect)) {
            return false;
        }
        double effectiveRadius = Math.min(
            sanctuary.territoryRadius(),
            radiusForTier(maximumRadius, tierFor(SanctuaryType.BEACON, effect))
        );
        return horizontalDistance <= effectiveRadius;
    }

    public int level(Sanctuary sanctuary, SanctuaryEffect effect) throws SQLException {
        int level = legacyRepository.getLevel(sanctuary.id(), effect);
        return Math.max(1, Math.min(level, effect.maximumLevel()));
    }

    public void setLevel(Sanctuary sanctuary, SanctuaryEffect effect, int level) throws SQLException {
        if (!isUnlocked(sanctuary, effect)) {
            throw new IllegalStateException("That effect is not unlocked at this Beacon tier.");
        }
        legacyRepository.setLevel(sanctuary.id(), effect, level);
    }

    public List<ActiveSanctuaryEffect> activeEffects(
        Sanctuary sanctuary,
        UUID playerId,
        double horizontalDistance,
        double maximumRadius
    ) throws SQLException {
        SanctuaryEffect.EffectTarget target = targetFor(sanctuary, playerId);
        if (target == null) {
            return List.of();
        }
        List<ActiveSanctuaryEffect> active = new ArrayList<>();
        for (AnchorEffectDefinition definition : definitions(SanctuaryType.BEACON, target)) {
            SanctuaryEffect effect = definition.effect();
            if (isWithinEffectRadius(sanctuary, effect, horizontalDistance, maximumRadius)) {
                active.add(new ActiveSanctuaryEffect(effect, level(sanctuary, effect)));
            }
        }
        return List.copyOf(active);
    }

    private SanctuaryEffect.EffectTarget targetFor(Sanctuary sanctuary, UUID playerId)
        throws SQLException {
        SanctuaryThreat threat = securityService.threat(sanctuary, playerId);
        return switch (threat) {
            case SAFE -> SanctuaryEffect.EffectTarget.SAFE;
            case HOSTILE -> SanctuaryEffect.EffectTarget.HOSTILE;
            case NEUTRAL -> null;
        };
    }

    private void requireAnchorRepository() {
        if (anchorRepository == null) {
            throw new IllegalStateException("Per-anchor effect repository is not configured");
        }
    }

    private static AnchorEffectDefinition definition(SanctuaryEffect effect, int tier) {
        return new AnchorEffectDefinition(effect, tier);
    }

    private static void validateMaximumRadius(double maximumRadius) {
        if (!Double.isFinite(maximumRadius) || maximumRadius <= 0.0) {
            throw new IllegalArgumentException("maximumRadius must be finite and greater than zero");
        }
    }

    public record AnchorEffectDefinition(SanctuaryEffect effect, int tier) {
        public AnchorEffectDefinition {
            Objects.requireNonNull(effect, "effect");
            if (tier < 1 || tier > EFFECT_TIER_COUNT) {
                throw new IllegalArgumentException("tier must be between 1 and 5");
            }
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

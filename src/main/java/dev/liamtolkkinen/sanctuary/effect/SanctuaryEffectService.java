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
        definition(AnchorEffect.REGENERATION, 1),
        definition(AnchorEffect.RESISTANCE, 2),
        definition(AnchorEffect.STRENGTH, 3),
        definition(AnchorEffect.HASTE, 4),
        definition(AnchorEffect.SPEED, 5)
    );
    private static final List<AnchorEffectDefinition> BEACON_HOSTILE = List.of(
        definition(AnchorEffect.WITHER, 1),
        definition(AnchorEffect.BLINDNESS, 2),
        definition(AnchorEffect.WEAKNESS, 3),
        definition(AnchorEffect.MINING_FATIGUE, 4),
        definition(AnchorEffect.ELYTRA_DISABLED, 5)
    );
    private static final List<AnchorEffectDefinition> CONDUIT_SAFE = List.of(
        definition(AnchorEffect.REGENERATION, 1),
        definition(AnchorEffect.CONDUIT_POWER, 2),
        definition(AnchorEffect.HASTE, 3),
        definition(AnchorEffect.DOLPHINS_GRACE, 4),
        definition(AnchorEffect.RESISTANCE, 5)
    );
    private static final List<AnchorEffectDefinition> CONDUIT_HOSTILE = List.of(
        definition(AnchorEffect.WITHER, 1),
        definition(AnchorEffect.BLINDNESS, 2),
        definition(AnchorEffect.MINING_FATIGUE, 3),
        definition(AnchorEffect.SLOWNESS, 4),
        definition(AnchorEffect.WEAKNESS, 5)
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

    public List<AnchorEffectDefinition> definitions(SanctuaryType type, AnchorEffect.Target target) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        return switch (type) {
            case BEACON -> target == AnchorEffect.Target.SAFE ? BEACON_SAFE : BEACON_HOSTILE;
            case CONDUIT -> target == AnchorEffect.Target.SAFE ? CONDUIT_SAFE : CONDUIT_HOSTILE;
        };
    }

    public int tierFor(SanctuaryType type, AnchorEffect effect) {
        return definitions(type, effect.target()).stream()
            .filter(definition -> definition.effect() == effect)
            .mapToInt(AnchorEffectDefinition::tier)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                effect.name() + " is not available for "
                    + type.name().toLowerCase(java.util.Locale.ROOT) + " anchors"
            ));
    }

    public AnchorEffect pairedEffect(SanctuaryType type, AnchorEffect effect) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(effect, "effect");
        int tier = tierFor(type, effect);
        AnchorEffect.Target pairedTarget = effect.target() == AnchorEffect.Target.SAFE
            ? AnchorEffect.Target.HOSTILE
            : AnchorEffect.Target.SAFE;
        return definitions(type, pairedTarget).stream()
            .filter(definition -> definition.tier() == tier)
            .map(AnchorEffectDefinition::effect)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No paired effect exists for " + effect.name() + " on " + type.name()
            ));
    }

    public int maximumAttunementLevel(SanctuaryType type, AnchorEffect effect) {
        AnchorEffect paired = pairedEffect(type, effect);
        return Math.max(effect.maximumLevel(), paired.maximumLevel());
    }

    public boolean isUnlocked(SanctuaryAnchor anchor, AnchorEffect effect) {
        try {
            return anchor.tier() >= tierFor(anchor.type(), effect);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean isWithinEffectRadius(
        SanctuaryAnchor anchor,
        AnchorEffect effect,
        double horizontalDistance,
        double maximumRadius
    ) {
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

    public int attunementLevel(SanctuaryAnchor anchor, AnchorEffect effect) throws SQLException {
        requireAnchorRepository();
        AnchorEffect paired = pairedEffect(anchor.type(), effect);
        int maximumLevel = maximumAttunementLevel(anchor.type(), effect);
        int ownLevel = anchorRepository.getLevel(anchor.id(), effect);
        int pairedLevel = anchorRepository.getLevel(anchor.id(), paired);
        int pairLevel = Math.max(ownLevel, pairedLevel);
        return Math.max(1, Math.min(pairLevel, maximumLevel));
    }

    public int level(SanctuaryAnchor anchor, AnchorEffect effect) throws SQLException {
        int pairLevel = attunementLevel(anchor, effect);
        return Math.min(pairLevel, effect.maximumLevel());
    }

    public void setLevel(SanctuaryAnchor anchor, AnchorEffect effect, int level) throws SQLException {
        requireAnchorRepository();
        if (!isUnlocked(anchor, effect)) {
            throw new IllegalStateException("That effect is not unlocked at this anchor tier.");
        }

        AnchorEffect paired = pairedEffect(anchor.type(), effect);
        if (!isUnlocked(anchor, paired)) {
            throw new IllegalStateException("The paired effect is not unlocked at this anchor tier.");
        }

        int maximumLevel = maximumAttunementLevel(anchor.type(), effect);
        if (level < 1 || level > maximumLevel) {
            throw new IllegalArgumentException(
                "Paired attunement level must be between 1 and " + maximumLevel
            );
        }

        int previousEffectLevel = anchorRepository.getLevel(anchor.id(), effect);
        int previousPairedLevel = anchorRepository.getLevel(anchor.id(), paired);
        int effectLevel = Math.min(level, effect.maximumLevel());
        int pairedLevel = Math.min(level, paired.maximumLevel());

        anchorRepository.setLevel(anchor.id(), effect, effectLevel);
        try {
            anchorRepository.setLevel(anchor.id(), paired, pairedLevel);
        } catch (SQLException | RuntimeException exception) {
            try {
                anchorRepository.setLevel(anchor.id(), effect, previousEffectLevel);
                anchorRepository.setLevel(anchor.id(), paired, previousPairedLevel);
            } catch (SQLException | RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    public List<ActiveAnchorEffect> activeAnchorEffects(
        Sanctuary sanctuary,
        SanctuaryAnchor anchor,
        UUID playerId,
        double horizontalDistance,
        double maximumRadius
    ) throws SQLException {
        AnchorEffect.Target target = anchorTargetFor(sanctuary, playerId);
        if (target == null) {
            return List.of();
        }
        List<ActiveAnchorEffect> active = new ArrayList<>();
        for (AnchorEffectDefinition definition : definitions(anchor.type(), target)) {
            if (isWithinEffectRadius(anchor, definition.effect(), horizontalDistance, maximumRadius)) {
                active.add(new ActiveAnchorEffect(definition.effect(), level(anchor, definition.effect())));
            }
        }
        return List.copyOf(active);
    }

    // Existing Beacon API remains unchanged for compatibility with the current shared UI/tests.
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
        int level = legacyRepository.getLevel(sanctuary.id(), effect);
        if (level < 1) {
            return 1;
        }
        return Math.min(level, effect.maximumLevel());
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
        SanctuaryEffect.EffectTarget target = legacyTargetFor(sanctuary, playerId);
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

    private AnchorEffect.Target anchorTargetFor(Sanctuary sanctuary, UUID playerId)
        throws SQLException {
        return switch (securityService.threat(sanctuary, playerId)) {
            case SAFE -> AnchorEffect.Target.SAFE;
            case HOSTILE -> AnchorEffect.Target.HOSTILE;
            case NEUTRAL -> null;
        };
    }

    private SanctuaryEffect.EffectTarget legacyTargetFor(Sanctuary sanctuary, UUID playerId)
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

    private static AnchorEffectDefinition definition(AnchorEffect effect, int tier) {
        return new AnchorEffectDefinition(effect, tier);
    }

    private static void validateMaximumRadius(double maximumRadius) {
        if (!Double.isFinite(maximumRadius) || maximumRadius <= 0.0) {
            throw new IllegalArgumentException("maximumRadius must be finite and greater than zero");
        }
    }

    public record AnchorEffectDefinition(AnchorEffect effect, int tier) {
        public AnchorEffectDefinition {
            Objects.requireNonNull(effect, "effect");
            if (tier < 1 || tier > EFFECT_TIER_COUNT) {
                throw new IllegalArgumentException("tier must be between 1 and 5");
            }
        }
    }

    public record ActiveAnchorEffect(AnchorEffect effect, int level) {
        public ActiveAnchorEffect {
            Objects.requireNonNull(effect, "effect");
            if (level < 1 || level > effect.maximumLevel()) {
                throw new IllegalArgumentException("level is outside the effect maximum");
            }
        }

        public int amplifier() {
            return level - 1;
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

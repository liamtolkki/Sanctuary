package dev.liamtolkkinen.sanctuary.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.security.SanctuaryBlacklistEntry;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityRepository;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustEntry;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SanctuaryAggressionEffectTest {
    @Test
    void activeAggressorReceivesHostileBeaconEffectsWithoutChangingRelationship() throws Exception {
        Instant now = Instant.now();
        UUID sanctuaryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Sanctuary sanctuary = new Sanctuary(
            sanctuaryId,
            ownerId,
            SanctuaryType.BEACON,
            "Aggression Effects",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            5,
            1,
            96.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
        SanctuaryAnchor anchor = new SanctuaryAnchor(
            UUID.randomUUID(),
            sanctuaryId,
            Optional.empty(),
            SanctuaryType.BEACON,
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            5,
            1,
            96.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            now,
            now
        );

        MemorySecurityRepository securityRepository = new MemorySecurityRepository();
        SanctuarySecurityService securityService = new SanctuarySecurityService(
            securityRepository,
            new SanctuaryPermissionService(new EmptyTrustRepository())
        );
        SanctuaryEffectService effectService = new SanctuaryEffectService(
            new MemoryLegacyEffectRepository(),
            new MemoryAnchorEffectRepository(),
            securityService
        );

        assertTrue(effectService.activeAnchorEffects(
            sanctuary,
            anchor,
            playerId,
            10.0,
            96.0
        ).isEmpty());

        securityService.markAggressive(sanctuary, playerId, now);

        var effects = effectService.activeAnchorEffects(
            sanctuary,
            anchor,
            playerId,
            10.0,
            96.0
        );
        assertFalse(effects.isEmpty());
        assertTrue(effects.stream().allMatch(active ->
            active.effect().target() == AnchorEffect.Target.HOSTILE
        ));
    }

    private static final class MemoryLegacyEffectRepository implements SanctuaryEffectRepository {
        @Override
        public int getLevel(UUID sanctuaryId, SanctuaryEffect effect) {
            return 1;
        }

        @Override
        public void setLevel(UUID sanctuaryId, SanctuaryEffect effect, int level) {
        }
    }

    private static final class MemoryAnchorEffectRepository implements AnchorEffectRepository {
        @Override
        public int getLevel(UUID anchorId, AnchorEffect effect) {
            return 1;
        }

        @Override
        public void setLevel(UUID anchorId, AnchorEffect effect, int level) {
        }
    }

    private static final class MemorySecurityRepository implements SanctuarySecurityRepository {
        private final Map<String, Instant> aggression = new HashMap<>();

        @Override
        public SanctuarySecurityMode getMode(UUID sanctuaryId) {
            return SanctuarySecurityMode.NORMAL;
        }

        @Override
        public void setMode(UUID sanctuaryId, SanctuarySecurityMode mode) {
        }

        @Override
        public boolean isBlacklisted(UUID sanctuaryId, UUID playerId) {
            return false;
        }

        @Override
        public List<SanctuaryBlacklistEntry> findBlacklistedPlayers(UUID sanctuaryId) {
            return List.of();
        }

        @Override
        public void addBlacklisted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
        }

        @Override
        public void removeBlacklisted(UUID sanctuaryId, UUID playerId) {
        }

        @Override
        public Optional<Instant> getAggressionUntil(UUID sanctuaryId, UUID playerId) {
            return Optional.ofNullable(aggression.get(key(sanctuaryId, playerId)));
        }

        @Override
        public void setAggressionUntil(UUID sanctuaryId, UUID playerId, Instant hostileUntil) {
            aggression.put(key(sanctuaryId, playerId), hostileUntil);
        }

        @Override
        public void clearAggression(UUID sanctuaryId, UUID playerId) {
            aggression.remove(key(sanctuaryId, playerId));
        }

        private static String key(UUID sanctuaryId, UUID playerId) {
            return sanctuaryId + ":" + playerId;
        }
    }

    private static final class EmptyTrustRepository implements SanctuaryTrustRepository {
        @Override
        public boolean isTrusted(UUID sanctuaryId, UUID playerId) {
            return false;
        }

        @Override
        public List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) {
            return List.of();
        }

        @Override
        public Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId) {
            return Set.of();
        }

        @Override
        public void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
        }

        @Override
        public void removeTrusted(UUID sanctuaryId, UUID playerId) {
        }

        @Override
        public void setCapability(
            UUID sanctuaryId,
            UUID playerId,
            SanctuaryCapability capability,
            boolean allowed
        ) {
        }
    }
}

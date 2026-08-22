package dev.liamtolkkinen.sanctuary.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SanctuaryEffectServiceTest {
    @Test
    void radiiAreFiveEqualSegmentsOfMaximumRadius() {
        Fixture fixture = new Fixture();
        assertEquals(19.2, fixture.service.segmentDelta(96.0), 0.0001);
        assertEquals(19.2, fixture.service.radiusForTier(96.0, 1), 0.0001);
        assertEquals(38.4, fixture.service.radiusForTier(96.0, 2), 0.0001);
        assertEquals(57.6, fixture.service.radiusForTier(96.0, 3), 0.0001);
        assertEquals(76.8, fixture.service.radiusForTier(96.0, 4), 0.0001);
        assertEquals(96.0, fixture.service.radiusForTier(96.0, 5), 0.0001);
    }

    @Test
    void effectsStackInwardByTheirOwnTierRadius() {
        Fixture fixture = new Fixture();
        Sanctuary sanctuary = fixture.sanctuary(5, 96.0);

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.SPEED, 90.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.HASTE, 90.0, 96.0));

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.SPEED, 50.0, 96.0));
        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.HASTE, 50.0, 96.0));
        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.STRENGTH, 50.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.RESISTANCE, 50.0, 96.0));
    }

    @Test
    void beaconTierLocksLaterEffectTiers() {
        Fixture fixture = new Fixture();
        Sanctuary sanctuary = fixture.sanctuary(2, 38.4);

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.REGENERATION, 10.0, 96.0));
        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.RESISTANCE, 30.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.STRENGTH, 10.0, 96.0));
    }

    @Test
    void trustedGetsPositiveNeutralGetsNoneAndBlacklistedGetsHostile() throws Exception {
        Fixture fixture = new Fixture();
        Sanctuary sanctuary = fixture.sanctuary(5, 96.0);
        UUID playerId = UUID.randomUUID();

        assertEquals(List.of(), fixture.service.activeEffects(sanctuary, playerId, 10.0, 96.0));

        fixture.trustRepository.trusted.add(playerId);
        assertTrue(fixture.service.activeEffects(sanctuary, playerId, 10.0, 96.0).stream()
            .allMatch(active -> active.effect().target() == SanctuaryEffect.EffectTarget.SAFE));

        fixture.trustRepository.trusted.clear();
        fixture.securityRepository.blacklisted.add(playerId);
        assertTrue(fixture.service.activeEffects(sanctuary, playerId, 10.0, 96.0).stream()
            .allMatch(active -> active.effect().target() == SanctuaryEffect.EffectTarget.HOSTILE));
    }


    @Test
    void hostileEffectsStackFromElytraAtThePerimeterToWitherAtTheCore() throws Exception {
        Fixture fixture = new Fixture();
        Sanctuary sanctuary = fixture.sanctuary(5, 96.0);

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.ELYTRA_DISABLED, 90.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.MINING_FATIGUE, 90.0, 96.0));

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.MINING_FATIGUE, 70.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.WEAKNESS, 70.0, 96.0));

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.WEAKNESS, 50.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.BLINDNESS, 50.0, 96.0));

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.BLINDNESS, 30.0, 96.0));
        assertFalse(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.WITHER, 30.0, 96.0));

        assertTrue(fixture.service.isWithinEffectRadius(sanctuary, SanctuaryEffect.WITHER, 10.0, 96.0));
    }

    private static final class Fixture {
        private final InMemoryEffectRepository effectRepository = new InMemoryEffectRepository();
        private final InMemoryTrustRepository trustRepository = new InMemoryTrustRepository();
        private final InMemorySecurityRepository securityRepository = new InMemorySecurityRepository();
        private final SanctuaryEffectService service = new SanctuaryEffectService(
            effectRepository,
            new SanctuarySecurityService(
                securityRepository,
                new SanctuaryPermissionService(trustRepository)
            )
        );

        private Sanctuary sanctuary(int tier, double radius) {
            Instant now = Instant.parse("2026-08-22T20:00:00Z");
            return new Sanctuary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SanctuaryType.BEACON,
                "Effects",
                Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
                tier,
                1,
                radius,
                SanctuaryState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                false,
                now,
                now
            );
        }
    }

    private static final class InMemoryEffectRepository implements SanctuaryEffectRepository {
        private final Map<String, Integer> levels = new HashMap<>();

        @Override
        public int getLevel(UUID sanctuaryId, SanctuaryEffect effect) {
            return levels.getOrDefault(sanctuaryId + ":" + effect.name(), 1);
        }

        @Override
        public void setLevel(UUID sanctuaryId, SanctuaryEffect effect, int level) {
            levels.put(sanctuaryId + ":" + effect.name(), level);
        }
    }

    private static final class InMemoryTrustRepository implements SanctuaryTrustRepository {
        private final Set<UUID> trusted = new HashSet<>();

        @Override
        public boolean isTrusted(UUID sanctuaryId, UUID playerId) {
            return trusted.contains(playerId);
        }

        @Override
        public void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
            trusted.add(playerId);
        }

        @Override
        public void removeTrusted(UUID sanctuaryId, UUID playerId) {
            trusted.remove(playerId);
        }

        @Override
        public Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId) {
            return EnumSet.noneOf(SanctuaryCapability.class);
        }

        @Override
        public void setCapability(UUID sanctuaryId, UUID playerId, SanctuaryCapability capability, boolean allowed) {
        }

        @Override
        public List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) {
            return List.of();
        }
    }

    private static final class InMemorySecurityRepository implements SanctuarySecurityRepository {
        private final Set<UUID> blacklisted = new HashSet<>();
        private SanctuarySecurityMode mode = SanctuarySecurityMode.NORMAL;

        @Override
        public SanctuarySecurityMode getMode(UUID sanctuaryId) {
            return mode;
        }

        @Override
        public void setMode(UUID sanctuaryId, SanctuarySecurityMode mode) {
            this.mode = mode;
        }

        @Override
        public boolean isBlacklisted(UUID sanctuaryId, UUID playerId) {
            return blacklisted.contains(playerId);
        }

        @Override
        public void addBlacklisted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
            blacklisted.add(playerId);
        }

        @Override
        public void removeBlacklisted(UUID sanctuaryId, UUID playerId) {
            blacklisted.remove(playerId);
        }

        @Override
        public List<SanctuaryBlacklistEntry> findBlacklistedPlayers(UUID sanctuaryId) {
            return List.of();
        }
    }
}

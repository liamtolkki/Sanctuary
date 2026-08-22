package dev.liamtolkkinen.sanctuary.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustEntry;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SanctuarySecurityServiceTest {
    @Test
    void normalAndLockdownResolveNeutralDifferently() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID neutral = UUID.randomUUID();
        Sanctuary sanctuary = sanctuary(owner);
        MemoryTrustRepository trust = new MemoryTrustRepository();
        MemorySecurityRepository security = new MemorySecurityRepository();
        SanctuarySecurityService service = new SanctuarySecurityService(
            security,
            new SanctuaryPermissionService(trust)
        );

        assertEquals(SanctuaryRelationship.NEUTRAL, service.relationship(sanctuary, neutral));
        assertEquals(SanctuaryThreat.NEUTRAL, service.threat(sanctuary, neutral));

        service.setMode(sanctuary, SanctuarySecurityMode.LOCKDOWN);
        assertEquals(SanctuaryThreat.HOSTILE, service.threat(sanctuary, neutral));
    }

    @Test
    void ownerAndTrustedStaySafeDuringLockdown() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        Sanctuary sanctuary = sanctuary(owner);
        MemoryTrustRepository trust = new MemoryTrustRepository();
        MemorySecurityRepository security = new MemorySecurityRepository();
        SanctuaryPermissionService permissionService = new SanctuaryPermissionService(trust);
        SanctuarySecurityService service = new SanctuarySecurityService(security, permissionService);

        permissionService.trust(sanctuary, trusted, Instant.now());
        service.setMode(sanctuary, SanctuarySecurityMode.LOCKDOWN);

        assertEquals(SanctuaryThreat.SAFE, service.threat(sanctuary, owner));
        assertEquals(SanctuaryThreat.SAFE, service.threat(sanctuary, trusted));
    }

    @Test
    void blacklistRemovesTrustAndTrustPreparationRemovesBlacklist() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Sanctuary sanctuary = sanctuary(owner);
        MemoryTrustRepository trust = new MemoryTrustRepository();
        MemorySecurityRepository security = new MemorySecurityRepository();
        SanctuaryPermissionService permissionService = new SanctuaryPermissionService(trust);
        SanctuarySecurityService service = new SanctuarySecurityService(security, permissionService);

        permissionService.trust(sanctuary, player, Instant.now());
        service.blacklist(sanctuary, player, Instant.now());
        assertEquals(SanctuaryRelationship.BLACKLISTED, service.relationship(sanctuary, player));

        service.prepareForTrust(sanctuary, player);
        permissionService.trust(sanctuary, player, Instant.now());
        assertEquals(SanctuaryRelationship.TRUSTED, service.relationship(sanctuary, player));
    }

    private static Sanctuary sanctuary(UUID owner) {
        Instant now = Instant.now();
        return new Sanctuary(
            UUID.randomUUID(), owner, SanctuaryType.BEACON, "Test", Optional.empty(),
            1, 1, 18.0, SanctuaryState.INACTIVE, Optional.empty(), Optional.empty(),
            false, now, now
        );
    }

    private static final class MemorySecurityRepository implements SanctuarySecurityRepository {
        private final Map<UUID, SanctuarySecurityMode> modes = new HashMap<>();
        private final Map<UUID, Map<UUID, Instant>> blacklist = new HashMap<>();

        @Override
        public SanctuarySecurityMode getMode(UUID sanctuaryId) {
            return modes.getOrDefault(sanctuaryId, SanctuarySecurityMode.NORMAL);
        }

        @Override
        public void setMode(UUID sanctuaryId, SanctuarySecurityMode mode) {
            modes.put(sanctuaryId, mode);
        }

        @Override
        public boolean isBlacklisted(UUID sanctuaryId, UUID playerId) {
            return blacklist.getOrDefault(sanctuaryId, Map.of()).containsKey(playerId);
        }

        @Override
        public List<SanctuaryBlacklistEntry> findBlacklistedPlayers(UUID sanctuaryId) {
            return blacklist.getOrDefault(sanctuaryId, Map.of()).entrySet().stream()
                .map(entry -> new SanctuaryBlacklistEntry(sanctuaryId, entry.getKey(), entry.getValue()))
                .toList();
        }

        @Override
        public void addBlacklisted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
            blacklist.computeIfAbsent(sanctuaryId, ignored -> new HashMap<>()).put(playerId, createdAt);
        }

        @Override
        public void removeBlacklisted(UUID sanctuaryId, UUID playerId) {
            blacklist.computeIfAbsent(sanctuaryId, ignored -> new HashMap<>()).remove(playerId);
        }
    }

    private static final class MemoryTrustRepository implements SanctuaryTrustRepository {
        private final Map<UUID, Map<UUID, Instant>> trusted = new HashMap<>();
        private final Map<String, Set<SanctuaryCapability>> capabilities = new HashMap<>();

        @Override
        public boolean isTrusted(UUID sanctuaryId, UUID playerId) {
            return trusted.getOrDefault(sanctuaryId, Map.of()).containsKey(playerId);
        }

        @Override
        public List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) {
            List<SanctuaryTrustEntry> result = new ArrayList<>();
            trusted.getOrDefault(sanctuaryId, Map.of()).forEach((playerId, createdAt) ->
                result.add(new SanctuaryTrustEntry(
                    sanctuaryId,
                    playerId,
                    createdAt,
                    capabilities.getOrDefault(key(sanctuaryId, playerId), Set.of())
                ))
            );
            return result;
        }

        @Override
        public Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId) {
            return capabilities.getOrDefault(key(sanctuaryId, playerId), Set.of());
        }

        @Override
        public void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
            trusted.computeIfAbsent(sanctuaryId, ignored -> new HashMap<>()).put(playerId, createdAt);
        }

        @Override
        public void removeTrusted(UUID sanctuaryId, UUID playerId) {
            trusted.computeIfAbsent(sanctuaryId, ignored -> new HashMap<>()).remove(playerId);
            capabilities.remove(key(sanctuaryId, playerId));
        }

        @Override
        public void setCapability(
            UUID sanctuaryId,
            UUID playerId,
            SanctuaryCapability capability,
            boolean allowed
        ) {
            Set<SanctuaryCapability> values = capabilities.computeIfAbsent(
                key(sanctuaryId, playerId),
                ignored -> EnumSet.noneOf(SanctuaryCapability.class)
            );
            if (allowed) {
                values.add(capability);
            } else {
                values.remove(capability);
            }
        }

        private static String key(UUID sanctuaryId, UUID playerId) {
            return sanctuaryId + ":" + playerId;
        }
    }
}

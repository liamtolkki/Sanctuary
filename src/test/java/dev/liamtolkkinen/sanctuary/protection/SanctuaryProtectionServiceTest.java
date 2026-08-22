package dev.liamtolkkinen.sanctuary.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
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

class SanctuaryProtectionServiceTest {
    @Test
    void outsideTerritoryIsNeverBlocked() throws Exception {
        Sanctuary sanctuary = sanctuary();
        SanctuaryProtectionService service = service(sanctuary, new FakeTrustRepository());

        assertTrue(service.findBlockingSanctuary(
            UUID.randomUUID(), SanctuaryCapability.BREAK, "world", 100, 100
        ).isEmpty());
    }

    @Test
    void untrustedPlayerIsBlockedInsideTerritory() throws Exception {
        Sanctuary sanctuary = sanctuary();
        SanctuaryProtectionService service = service(sanctuary, new FakeTrustRepository());
        UUID player = UUID.randomUUID();

        assertEquals(sanctuary.id(), service.findBlockingSanctuary(
            player, SanctuaryCapability.BREAK, "world", 0.5, 0.5
        ).orElseThrow().id());
    }

    @Test
    void ownerIsAllowedInsideTerritory() throws Exception {
        Sanctuary sanctuary = sanctuary();
        SanctuaryProtectionService service = service(sanctuary, new FakeTrustRepository());

        assertTrue(service.findBlockingSanctuary(
            sanctuary.ownerId(), SanctuaryCapability.BREAK, "world", 0.5, 0.5
        ).isEmpty());
    }

    @Test
    void trustedPlayerOnlyGetsGrantedCapability() throws Exception {
        Sanctuary sanctuary = sanctuary();
        FakeTrustRepository trust = new FakeTrustRepository();
        SanctuaryPermissionService permissions = new SanctuaryPermissionService(trust);
        UUID player = UUID.randomUUID();
        permissions.trust(sanctuary, player, Instant.now());
        permissions.setCapability(sanctuary, player, SanctuaryCapability.CONTAINER, true);
        SanctuaryProtectionService service = new SanctuaryProtectionService(
            new FakeSanctuaryRepository(sanctuary), new TerritoryPresenceService(), permissions
        );

        assertTrue(service.findBlockingSanctuary(
            player, SanctuaryCapability.CONTAINER, "world", 0.5, 0.5
        ).isEmpty());
        assertEquals(sanctuary.id(), service.findBlockingSanctuary(
            player, SanctuaryCapability.BUILD, "world", 0.5, 0.5
        ).orElseThrow().id());
    }

    private static SanctuaryProtectionService service(Sanctuary sanctuary, FakeTrustRepository trust) {
        return new SanctuaryProtectionService(
            new FakeSanctuaryRepository(sanctuary),
            new TerritoryPresenceService(),
            new SanctuaryPermissionService(trust)
        );
    }

    private static Sanctuary sanctuary() {
        Instant now = Instant.parse("2026-08-22T12:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(), UUID.randomUUID(), SanctuaryType.BEACON, "Protected",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1, 1, 18.0, SanctuaryState.ACTIVE,
            Optional.empty(), Optional.empty(), false, now, now
        );
    }

    private static final class FakeSanctuaryRepository implements SanctuaryRepository {
        private final Sanctuary sanctuary;
        FakeSanctuaryRepository(Sanctuary sanctuary) { this.sanctuary = sanctuary; }
        @Override public Optional<Sanctuary> findById(UUID id) { return sanctuary.id().equals(id) ? Optional.of(sanctuary) : Optional.empty(); }
        @Override public List<Sanctuary> findByOwner(UUID ownerId) { return sanctuary.ownerId().equals(ownerId) ? List.of(sanctuary) : List.of(); }
        @Override public List<Sanctuary> findAll() { return List.of(sanctuary); }
        @Override public List<Sanctuary> findActiveInWorld(String world) { return sanctuary.position().orElseThrow().world().equals(world) ? List.of(sanctuary) : List.of(); }
        @Override public void delete(UUID id) { }
        @Override public void save(Sanctuary sanctuary) { }
    }

    private static final class FakeTrustRepository implements SanctuaryTrustRepository {
        private final Map<String, Instant> trusted = new HashMap<>();
        private final Map<String, EnumSet<SanctuaryCapability>> capabilities = new HashMap<>();
        @Override public boolean isTrusted(UUID sanctuaryId, UUID playerId) { return trusted.containsKey(key(sanctuaryId, playerId)); }
        @Override public List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) { return new ArrayList<>(); }
        @Override public Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId) {
            EnumSet<SanctuaryCapability> values = capabilities.get(key(sanctuaryId, playerId));
            return values == null ? Set.of() : Set.copyOf(values);
        }
        @Override public void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) { trusted.put(key(sanctuaryId, playerId), createdAt); }
        @Override public void removeTrusted(UUID sanctuaryId, UUID playerId) { trusted.remove(key(sanctuaryId, playerId)); capabilities.remove(key(sanctuaryId, playerId)); }
        @Override public void setCapability(UUID sanctuaryId, UUID playerId, SanctuaryCapability capability, boolean allowed) {
            String key = key(sanctuaryId, playerId);
            if (allowed) capabilities.computeIfAbsent(key, ignored -> EnumSet.noneOf(SanctuaryCapability.class)).add(capability);
            else if (capabilities.containsKey(key)) capabilities.get(key).remove(capability);
        }
        private static String key(UUID sanctuaryId, UUID playerId) { return sanctuaryId + ":" + playerId; }
    }
}

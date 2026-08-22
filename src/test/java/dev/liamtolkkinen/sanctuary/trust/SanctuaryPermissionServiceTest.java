package dev.liamtolkkinen.sanctuary.trust;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
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

class SanctuaryPermissionServiceTest {
    @Test
    void ownerAlwaysHasEveryCapability() throws Exception {
        FakeRepository repository = new FakeRepository();
        SanctuaryPermissionService service = new SanctuaryPermissionService(repository);
        Sanctuary sanctuary = sanctuary();

        for (SanctuaryCapability capability : SanctuaryCapability.values()) {
            assertTrue(service.hasCapability(sanctuary, sanctuary.ownerId(), capability));
        }
    }

    @Test
    void trustedPlayerOnlyHasExplicitlyGrantedCapabilities() throws Exception {
        FakeRepository repository = new FakeRepository();
        SanctuaryPermissionService service = new SanctuaryPermissionService(repository);
        Sanctuary sanctuary = sanctuary();
        UUID playerId = UUID.randomUUID();

        service.trust(sanctuary, playerId, Instant.now());
        assertTrue(service.isTrusted(sanctuary, playerId));
        assertFalse(service.hasCapability(sanctuary, playerId, SanctuaryCapability.BUILD));

        service.setCapability(sanctuary, playerId, SanctuaryCapability.CONTAINER, true);

        assertTrue(service.hasCapability(sanctuary, playerId, SanctuaryCapability.CONTAINER));
        assertFalse(service.hasCapability(sanctuary, playerId, SanctuaryCapability.BREAK));
    }

    @Test
    void untrustRemovesAllCapabilities() throws Exception {
        FakeRepository repository = new FakeRepository();
        SanctuaryPermissionService service = new SanctuaryPermissionService(repository);
        Sanctuary sanctuary = sanctuary();
        UUID playerId = UUID.randomUUID();

        service.trust(sanctuary, playerId, Instant.now());
        service.setCapability(sanctuary, playerId, SanctuaryCapability.BUILD, true);
        service.setCapability(sanctuary, playerId, SanctuaryCapability.INTERACT, true);
        service.untrust(sanctuary, playerId);

        assertFalse(service.isTrusted(sanctuary, playerId));
        assertFalse(service.hasCapability(sanctuary, playerId, SanctuaryCapability.BUILD));
        assertTrue(repository.findCapabilities(sanctuary.id(), playerId).isEmpty());
    }

    @Test
    void capabilityCannotBeGrantedBeforeTrust() {
        FakeRepository repository = new FakeRepository();
        SanctuaryPermissionService service = new SanctuaryPermissionService(repository);
        Sanctuary sanctuary = sanctuary();

        assertThrows(
            IllegalStateException.class,
            () -> service.setCapability(
                sanctuary,
                UUID.randomUUID(),
                SanctuaryCapability.BUILD,
                true
            )
        );
    }

    private static Sanctuary sanctuary() {
        Instant now = Instant.parse("2026-08-22T12:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            "Test Sanctuary",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            18.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }

    private static final class FakeRepository implements SanctuaryTrustRepository {
        private final Map<String, Instant> trusted = new HashMap<>();
        private final Map<String, EnumSet<SanctuaryCapability>> capabilities = new HashMap<>();

        @Override
        public boolean isTrusted(UUID sanctuaryId, UUID playerId) {
            return trusted.containsKey(key(sanctuaryId, playerId));
        }

        @Override
        public List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) {
            List<SanctuaryTrustEntry> result = new ArrayList<>();
            trusted.forEach((key, createdAt) -> {
                String prefix = sanctuaryId + ":";
                if (key.startsWith(prefix)) {
                    UUID playerId = UUID.fromString(key.substring(prefix.length()));
                    result.add(new SanctuaryTrustEntry(
                        sanctuaryId,
                        playerId,
                        createdAt,
                        findCapabilities(sanctuaryId, playerId)
                    ));
                }
            });
            return result;
        }

        @Override
        public Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId) {
            EnumSet<SanctuaryCapability> values = capabilities.get(key(sanctuaryId, playerId));
            return values == null ? Set.of() : Set.copyOf(values);
        }

        @Override
        public void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) {
            trusted.putIfAbsent(key(sanctuaryId, playerId), createdAt);
        }

        @Override
        public void removeTrusted(UUID sanctuaryId, UUID playerId) {
            trusted.remove(key(sanctuaryId, playerId));
            capabilities.remove(key(sanctuaryId, playerId));
        }

        @Override
        public void setCapability(
            UUID sanctuaryId,
            UUID playerId,
            SanctuaryCapability capability,
            boolean allowed
        ) {
            String key = key(sanctuaryId, playerId);
            if (allowed) {
                capabilities.computeIfAbsent(key, ignored -> EnumSet.noneOf(SanctuaryCapability.class))
                    .add(capability);
            } else {
                EnumSet<SanctuaryCapability> values = capabilities.get(key);
                if (values != null) {
                    values.remove(capability);
                }
            }
        }

        private static String key(UUID sanctuaryId, UUID playerId) {
            return sanctuaryId + ":" + playerId;
        }
    }
}

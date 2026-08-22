package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebugBeaconRegistrationServiceTest {
    @Test
    void registrationCreatesInactiveEphemeralSyntheticOwnerSanctuary() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        DebugBeaconRegistrationService service = new DebugBeaconRegistrationService(
            repository,
            Clock.fixed(Instant.parse("2026-08-22T06:00:00Z"), ZoneOffset.UTC)
        );

        Sanctuary sanctuary = service.register(100.0);

        assertTrue(sanctuary.debugEphemeral());
        assertEquals(SanctuaryState.INACTIVE, sanctuary.state());
        assertTrue(sanctuary.position().isEmpty());
        assertEquals(15, sanctuary.ownerId().version());
        assertEquals(sanctuary, repository.findById(sanctuary.id()).orElseThrow());
    }

    @Test
    void debugRegistrationCanCreateMaximumTierBeacon() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        DebugBeaconRegistrationService service = new DebugBeaconRegistrationService(
            repository,
            Clock.fixed(Instant.parse("2026-08-22T06:00:00Z"), ZoneOffset.UTC)
        );

        Sanctuary sanctuary = service.register(96.0, 5);

        assertEquals(5, sanctuary.tier());
        assertEquals(96.0, sanctuary.territoryRadius());
    }

    @Test
    void syntheticOwnerIsDerivedFromAnchorButCannotEqualIt() {
        UUID anchor = UUID.randomUUID();
        UUID owner = DebugBeaconRegistrationService.syntheticOwnerId(anchor);

        assertFalse(anchor.equals(owner));
        assertEquals(15, owner.version());
    }

    private static final class InMemoryRepository implements SanctuaryRepository {
        private final Map<UUID, Sanctuary> values = new LinkedHashMap<>();

        @Override
        public Optional<Sanctuary> findById(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public List<Sanctuary> findByOwner(UUID ownerId) {
            return values.values().stream().filter(v -> v.ownerId().equals(ownerId)).toList();
        }

        @Override
        public List<Sanctuary> findAll() {
            return List.copyOf(values.values());
        }

        @Override
        public List<Sanctuary> findActiveInWorld(String world) {
            return List.of();
        }

        @Override
        public void delete(UUID id) {
            values.remove(id);
        }

        @Override
        public void save(Sanctuary sanctuary) {
            values.put(sanctuary.id(), sanctuary);
        }
    }
}

package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.liamtolkkinen.sanctuary.anchor.AnchorPlacementException;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacementSpacingServiceTest {
    @Test
    void differentOwnersMustRespectFutureGrowthSpacing() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID existingOwner = UUID.randomUUID();
        repository.save(active(UUID.randomUUID(), existingOwner, 0, 0));
        PlacementSpacingService service = new PlacementSpacingService(repository);

        assertThrows(
            AnchorPlacementException.class,
            () -> service.validatePlacement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new SanctuaryPosition("world", 143, 90, 0),
                64.0,
                16.0
            )
        );

        assertDoesNotThrow(() -> service.validatePlacement(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new SanctuaryPosition("world", 144, 20, 0),
            64.0,
            16.0
        ));
    }

    @Test
    void sameOwnerOverlapIsAllowed() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        UUID owner = UUID.randomUUID();
        repository.save(active(UUID.randomUUID(), owner, 0, 0));
        PlacementSpacingService service = new PlacementSpacingService(repository);

        assertDoesNotThrow(() -> service.validatePlacement(
            UUID.randomUUID(),
            owner,
            new SanctuaryPosition("world", 1, 200, 1),
            64.0,
            16.0
        ));
    }

    @Test
    void otherWorldsDoNotConflict() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(active(UUID.randomUUID(), UUID.randomUUID(), 0, 0));
        PlacementSpacingService service = new PlacementSpacingService(repository);

        assertDoesNotThrow(() -> service.validatePlacement(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new SanctuaryPosition("world_nether", 0, 64, 0),
            64.0,
            16.0
        ));
    }

    private static Sanctuary active(UUID id, UUID owner, int x, int z) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new Sanctuary(
            id,
            owner,
            SanctuaryType.BEACON,
            "Test",
            Optional.of(new SanctuaryPosition("world", x, 64, z)),
            1,
            1,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }

    private static final class InMemoryRepository implements SanctuaryRepository {
        private final Map<UUID, Sanctuary> values = new LinkedHashMap<>();

        @Override
        public Optional<Sanctuary> findById(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public List<Sanctuary> findByOwner(UUID ownerId) {
            return values.values().stream()
                .filter(value -> value.ownerId().equals(ownerId))
                .toList();
        }

        @Override
        public List<Sanctuary> findAll() {
            return List.copyOf(values.values());
        }

        @Override
        public List<Sanctuary> findActiveInWorld(String world) {
            List<Sanctuary> result = new ArrayList<>();
            for (Sanctuary sanctuary : values.values()) {
                if (sanctuary.state() == SanctuaryState.ACTIVE
                    && sanctuary.position().isPresent()
                    && sanctuary.position().orElseThrow().world().equals(world)) {
                    result.add(sanctuary);
                }
            }
            return List.copyOf(result);
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

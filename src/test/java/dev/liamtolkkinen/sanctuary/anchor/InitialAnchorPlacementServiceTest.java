package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitialAnchorPlacementServiceTest {
    private InMemoryRepository repository;
    private InitialAnchorPlacementService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        service = new InitialAnchorPlacementService(
            repository,
            Clock.fixed(
                Instant.parse("2026-08-22T04:00:00Z"),
                ZoneOffset.UTC
            )
        );
    }

    @Test
    void firstPlacementCreatesOneActiveOwnedSanctuary() throws Exception {
        UUID anchorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AnchorMetadata metadata = new AnchorMetadata(
            anchorId,
            Optional.empty(),
            1,
            1
        ).bind(ownerId);
        SanctuaryPosition position = new SanctuaryPosition(
            "world",
            25,
            72,
            -40
        );

        Sanctuary created = service.createBeaconSanctuary(
            metadata,
            "Liam",
            position,
            100.0,
            64.0,
            16.0
        );

        assertEquals(anchorId, created.id());
        assertEquals(ownerId, created.ownerId());
        assertEquals(SanctuaryType.BEACON, created.type());
        assertEquals("Liam's Sanctuary", created.name());
        assertEquals(Optional.of(position), created.position());
        assertEquals(1, created.tier());
        assertEquals(1, created.anchorGeneration());
        assertEquals(100.0, created.territoryArea());
        assertEquals(SanctuaryState.ACTIVE, created.state());
        assertEquals(1, repository.values.size());
        assertEquals(created, repository.findById(anchorId).orElseThrow());
    }

    @Test
    void unboundMetadataCannotCreatePersistentSanctuary() {
        AnchorMetadata metadata = new AnchorMetadata(
            UUID.randomUUID(),
            Optional.empty(),
            1,
            1
        );

        assertThrows(
            AnchorPlacementException.class,
            () -> service.createBeaconSanctuary(
                metadata,
                "Liam",
                new SanctuaryPosition("world", 0, 64, 0),
                100.0,
                64.0,
                16.0
            )
        );
        assertEquals(0, repository.values.size());
    }

    @Test
    void existingAnchorIdIsNotOverwritten() throws Exception {
        UUID anchorId = UUID.randomUUID();
        UUID firstOwner = UUID.randomUUID();
        AnchorMetadata firstMetadata = new AnchorMetadata(
            anchorId,
            Optional.of(firstOwner),
            1,
            1
        );
        Sanctuary existing = service.createBeaconSanctuary(
            firstMetadata,
            "First",
            new SanctuaryPosition("world", 1, 64, 1),
            100.0,
            64.0,
            16.0
        );

        AnchorMetadata secondMetadata = new AnchorMetadata(
            anchorId,
            Optional.of(UUID.randomUUID()),
            1,
            1
        );

        assertThrows(
            AnchorPlacementException.class,
            () -> service.createBeaconSanctuary(
                secondMetadata,
                "Second",
                new SanctuaryPosition("world", 2, 64, 2),
                100.0,
                64.0,
                16.0
            )
        );
        assertEquals(1, repository.values.size());
        assertEquals(existing, repository.findById(anchorId).orElseThrow());
    }


    @Test
    void firstPlacementRejectsDifferentOwnerInsideReservedSpacing() throws Exception {
        UUID existingId = UUID.randomUUID();
        UUID existingOwner = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-22T03:00:00Z");
        repository.save(new Sanctuary(
            existingId,
            existingOwner,
            SanctuaryType.BEACON,
            "Existing",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        ));

        AnchorMetadata metadata = new AnchorMetadata(
            UUID.randomUUID(),
            Optional.of(UUID.randomUUID()),
            1,
            1
        );

        assertThrows(
            AnchorPlacementException.class,
            () -> service.createBeaconSanctuary(
                metadata,
                "Second",
                new SanctuaryPosition("world", 100, 70, 0),
                100.0,
                64.0,
                16.0
            )
        );
        assertEquals(1, repository.values.size());
    }

    @Test
    void firstPlacementAllowsSameOwnerOverlap() throws Exception {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-22T03:00:00Z");
        repository.save(new Sanctuary(
            UUID.randomUUID(),
            owner,
            SanctuaryType.BEACON,
            "Existing",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        ));

        AnchorMetadata metadata = new AnchorMetadata(
            UUID.randomUUID(),
            Optional.of(owner),
            1,
            1
        );

        Sanctuary created = service.createBeaconSanctuary(
            metadata,
            "SameOwner",
            new SanctuaryPosition("world", 1, 64, 1),
            100.0,
            64.0,
            16.0
        );
        assertEquals(SanctuaryState.ACTIVE, created.state());
        assertEquals(2, repository.values.size());
    }

    private static final class InMemoryRepository implements SanctuaryRepository {
        private final Map<UUID, Sanctuary> values = new LinkedHashMap<>();

        @Override
        public Optional<Sanctuary> findById(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public List<Sanctuary> findByOwner(UUID ownerId) {
            List<Sanctuary> result = new ArrayList<>();
            for (Sanctuary sanctuary : values.values()) {
                if (sanctuary.ownerId().equals(ownerId)) {
                    result.add(sanctuary);
                }
            }
            return List.copyOf(result);
        }


        @Override
        public List<Sanctuary> findAll() {
            return List.copyOf(values.values());
        }

        @Override
        public List<Sanctuary> findActiveInWorld(String world) {
            return values.values().stream()
                .filter(value -> value.state() == SanctuaryState.ACTIVE)
                .filter(value -> value.position().isPresent())
                .filter(value -> value.position().orElseThrow().world().equals(world))
                .toList();
        }

        @Override
        public void delete(UUID id) {
            values.remove(id);
        }

        @Override
        public void save(Sanctuary sanctuary) throws SQLException {
            values.put(sanctuary.id(), sanctuary);
        }
    }
}

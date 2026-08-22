package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Clock;
import java.time.Duration;
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

class AnchorLifecycleServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-22T04:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-22T05:00:00Z");

    private InMemoryRepository repository;
    private AnchorLifecycleService service;
    private UUID anchorId;
    private UUID ownerId;
    private SanctuaryPosition originalPosition;

    @BeforeEach
    void setUp() throws Exception {
        repository = new InMemoryRepository();
        service = new AnchorLifecycleService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        anchorId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        originalPosition = new SanctuaryPosition("world", 10, 64, 20);
        repository.save(activeSanctuary(1));
    }

    @Test
    void breakingDeactivatesWithoutChangingIdentity() throws Exception {
        Sanctuary inactive = service.deactivateForBreak(
            metadata(1),
            ownerId,
            originalPosition,
            false
        );

        assertEquals(anchorId, inactive.id());
        assertEquals(ownerId, inactive.ownerId());
        assertEquals(SanctuaryState.INACTIVE, inactive.state());
        assertTrue(inactive.position().isEmpty());
        assertEquals(2, inactive.anchorGeneration());
        assertEquals(CREATED_AT, inactive.createdAt());
        assertEquals(NOW, inactive.updatedAt());
    }

    @Test
    void nonOwnerCannotBreakUnlessAdminOverrideIsUsed() {
        UUID stranger = UUID.randomUUID();

        assertThrows(
            AnchorPlacementException.class,
            () -> service.deactivateForBreak(
                metadata(1),
                stranger,
                originalPosition,
                false
            )
        );
    }

    @Test
    void boundBeaconReactivatesSameSanctuaryAtNewLocation() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);
        SanctuaryPosition newPosition = new SanctuaryPosition("world_nether", -4, 80, 9);

        Sanctuary active = service.reactivate(metadata(2), ownerId, newPosition);

        assertEquals(anchorId, active.id());
        assertEquals("Home", active.name());
        assertEquals(ownerId, active.ownerId());
        assertEquals(Optional.of(newPosition), active.position());
        assertEquals(SanctuaryState.ACTIVE, active.state());
        assertEquals(1, repository.values.size());
    }

    @Test
    void staleGenerationCannotReactivate() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);
        AnchorRecoveryResult recovered = service.recover(anchorId, ownerId, Duration.ZERO);

        assertEquals(3, recovered.metadata().generation());
        assertThrows(
            AnchorPlacementException.class,
            () -> service.reactivate(
                metadata(2),
                ownerId,
                new SanctuaryPosition("world", 30, 70, 30)
            )
        );
    }

    @Test
    void recordedDestructionPermanentlyDestroysInactiveSanctuary() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);

        Optional<Sanctuary> result = service.recordDestruction(metadata(2), "DESPAWN");

        assertTrue(result.isPresent());
        Sanctuary destroyed = result.orElseThrow();
        assertEquals(SanctuaryState.DESTROYED, destroyed.state());
        assertEquals(Optional.of(NOW), destroyed.destroyedAt());
        assertEquals(Optional.of("DESPAWN"), destroyed.destructionReason());
        assertThrows(
            AnchorRecoveryException.class,
            () -> service.recover(anchorId, ownerId, Duration.ZERO)
        );
    }

    @Test
    void destructionOfStaleOrActiveCopyDoesNotDestroySanctuary() throws Exception {
        Optional<Sanctuary> activeResult = service.recordDestruction(metadata(1), "DEATH");
        assertTrue(activeResult.isEmpty());
        assertEquals(SanctuaryState.ACTIVE, repository.findById(anchorId).orElseThrow().state());

        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);
        AnchorRecoveryResult recovered = service.recover(anchorId, ownerId, Duration.ZERO);
        assertEquals(3, recovered.metadata().generation());

        Optional<Sanctuary> staleResult = service.recordDestruction(metadata(2), "DESPAWN");
        assertTrue(staleResult.isEmpty());
        assertEquals(SanctuaryState.INACTIVE, repository.findById(anchorId).orElseThrow().state());
    }

    @Test
    void recoveryAdvancesGenerationAndPreservesSanctuaryState() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);

        AnchorRecoveryResult result = service.recover(anchorId, ownerId, Duration.ZERO);

        assertEquals(3, result.metadata().generation());
        assertEquals(anchorId, result.metadata().anchorId());
        assertEquals(ownerId, result.metadata().ownerId().orElseThrow());
        assertEquals(3, result.sanctuary().anchorGeneration());
        assertEquals(SanctuaryState.INACTIVE, result.sanctuary().state());
        assertFalse(result.sanctuary().destroyedAt().isPresent());
    }

    @Test
    void recoveryHonorsCooldown() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);

        AnchorRecoveryException exception = assertThrows(
            AnchorRecoveryException.class,
            () -> service.recover(anchorId, ownerId, Duration.ofHours(2))
        );

        assertTrue(exception.getMessage().contains("7200"));
        assertEquals(2, repository.findById(anchorId).orElseThrow().anchorGeneration());
    }

    @Test
    void anotherPlayerCannotRecoverBeacon() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);

        assertThrows(
            AnchorRecoveryException.class,
            () -> service.recover(anchorId, UUID.randomUUID(), Duration.ZERO)
        );
    }

    private AnchorMetadata metadata(int generation) {
        return new AnchorMetadata(
            anchorId,
            Optional.of(ownerId),
            1,
            generation
        );
    }

    private Sanctuary activeSanctuary(int generation) {
        return new Sanctuary(
            anchorId,
            ownerId,
            SanctuaryType.BEACON,
            "Home",
            Optional.of(originalPosition),
            1,
            generation,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            CREATED_AT,
            CREATED_AT
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
        public void save(Sanctuary sanctuary) {
            values.put(sanctuary.id(), sanctuary);
        }
    }
}

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
    void registrationLookupDetectsMissingDatabaseRecord() throws Exception {
        assertTrue(service.hasRegisteredSanctuary(anchorId));

        repository.delete(anchorId);

        assertFalse(service.hasRegisteredSanctuary(anchorId));
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

        Sanctuary active = service.reactivate(metadata(2), ownerId, newPosition, 64.0, 16.0, false);

        assertEquals(anchorId, active.id());
        assertEquals("Home", active.name());
        assertEquals(ownerId, active.ownerId());
        assertEquals(Optional.of(newPosition), active.position());
        assertEquals(SanctuaryState.ACTIVE, active.state());
        assertEquals(1, repository.values.size());
    }


    @Test
    void reactivationEnforcesDifferentOwnerSpacing() throws Exception {
        service.deactivateForBreak(metadata(1), ownerId, originalPosition, false);
        UUID otherOwner = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        repository.save(new Sanctuary(
            otherId,
            otherOwner,
            SanctuaryType.BEACON,
            "Other",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            CREATED_AT,
            CREATED_AT
        ));

        assertThrows(
            AnchorPlacementException.class,
            () -> service.reactivate(
                metadata(2),
                ownerId,
                new SanctuaryPosition("world", 100, 100, 0),
                64.0,
                16.0,
                false
            )
        );
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
                new SanctuaryPosition("world", 30, 70, 30),
                64.0,
                16.0,
                false
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


    @Test
    void ephemeralDebugBeaconIsDeletedWhenBrokenAndDropsNoPersistentRecord() throws Exception {
        repository.values.clear();
        UUID debugOwner = DebugBeaconRegistrationService.syntheticOwnerId(anchorId);
        Sanctuary debug = new Sanctuary(
            anchorId,
            debugOwner,
            SanctuaryType.BEACON,
            "Debug Sanctuary",
            Optional.of(originalPosition),
            1,
            1,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            true,
            CREATED_AT,
            CREATED_AT
        );
        repository.save(debug);
        AnchorMetadata debugMetadata = new AnchorMetadata(
            anchorId,
            Optional.of(debugOwner),
            1,
            1
        );

        AnchorBreakResult result = service.breakAnchor(
            debugMetadata,
            UUID.randomUUID(),
            originalPosition,
            true
        );

        assertTrue(result.deleted());
        assertTrue(repository.findById(anchorId).isEmpty());
    }

    @Test
    void adminMayPlaceEphemeralDebugBeaconForSyntheticOwner() throws Exception {
        repository.values.clear();
        UUID debugOwner = DebugBeaconRegistrationService.syntheticOwnerId(anchorId);
        Sanctuary debug = new Sanctuary(
            anchorId,
            debugOwner,
            SanctuaryType.BEACON,
            "Debug Sanctuary",
            Optional.empty(),
            1,
            1,
            100.0,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            true,
            CREATED_AT,
            CREATED_AT
        );
        repository.save(debug);
        AnchorMetadata debugMetadata = new AnchorMetadata(
            anchorId,
            Optional.of(debugOwner),
            1,
            1
        );
        SanctuaryPosition position = new SanctuaryPosition("world", 50, 64, 50);

        assertThrows(
            AnchorPlacementException.class,
            () -> service.reactivate(
                debugMetadata,
                UUID.randomUUID(),
                position,
                64.0,
                16.0,
                false
            )
        );

        Sanctuary active = service.reactivate(
            debugMetadata,
            UUID.randomUUID(),
            position,
            64.0,
            16.0,
            true
        );
        assertEquals(SanctuaryState.ACTIVE, active.state());
        assertTrue(active.debugEphemeral());
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
            false,
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
        public void save(Sanctuary sanctuary) {
            values.put(sanctuary.id(), sanctuary);
        }
    }
}

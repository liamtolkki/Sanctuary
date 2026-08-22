package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.territory.PlacementSpacingService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AnchorLifecycleService {
    private final SanctuaryRepository repository;
    private final PlacementSpacingService spacingService;
    private final Clock clock;

    public AnchorLifecycleService(SanctuaryRepository repository) {
        this(repository, new PlacementSpacingService(repository), Clock.systemUTC());
    }

    AnchorLifecycleService(SanctuaryRepository repository, Clock clock) {
        this(repository, new PlacementSpacingService(repository), clock);
    }

    AnchorLifecycleService(
        SanctuaryRepository repository,
        PlacementSpacingService spacingService,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.spacingService = Objects.requireNonNull(spacingService, "spacingService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AnchorBreakResult breakAnchor(
        AnchorMetadata metadata,
        UUID breakerId,
        SanctuaryPosition currentPosition,
        boolean adminOverride
    ) throws SQLException, AnchorPlacementException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(breakerId, "breakerId");
        Objects.requireNonNull(currentPosition, "currentPosition");

        Sanctuary sanctuary = requireMatchingSanctuary(metadata);
        if (sanctuary.state() != SanctuaryState.ACTIVE) {
            throw new AnchorPlacementException("This Sanctuary is not currently active");
        }
        if (!sanctuary.position().equals(Optional.of(currentPosition))) {
            throw new AnchorPlacementException(
                "This Beacon does not match the registered Sanctuary location"
            );
        }
        if (!adminOverride && !sanctuary.ownerId().equals(breakerId)) {
            throw new AnchorPlacementException("Only the Sanctuary owner may break this Beacon");
        }

        if (sanctuary.debugEphemeral()) {
            repository.delete(sanctuary.id());
            return new AnchorBreakResult(sanctuary, true);
        }

        if (sanctuary.anchorGeneration() == Integer.MAX_VALUE) {
            throw new AnchorPlacementException("This Sanctuary cannot advance to another Beacon generation");
        }

        Instant now = clock.instant();
        Sanctuary inactive = copy(
            sanctuary,
            Optional.empty(),
            sanctuary.anchorGeneration() + 1,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            now
        );
        repository.save(inactive);
        return new AnchorBreakResult(inactive, false);
    }

    public Sanctuary deactivateForBreak(
        AnchorMetadata metadata,
        UUID breakerId,
        SanctuaryPosition currentPosition,
        boolean adminOverride
    ) throws SQLException, AnchorPlacementException {
        Sanctuary sanctuary = requireMatchingSanctuary(metadata);
        if (sanctuary.debugEphemeral()) {
            throw new AnchorPlacementException(
                "Ephemeral debug Sanctuaries must use the delete-on-break lifecycle"
            );
        }
        return breakAnchor(
            metadata,
            breakerId,
            currentPosition,
            adminOverride
        ).sanctuary();
    }

    public Sanctuary reactivate(
        AnchorMetadata metadata,
        UUID placerId,
        SanctuaryPosition newPosition,
        double maximumRadius,
        double spacingMargin,
        boolean adminOverride
    ) throws SQLException, AnchorPlacementException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(placerId, "placerId");
        Objects.requireNonNull(newPosition, "newPosition");

        Sanctuary sanctuary = requireMatchingSanctuary(metadata);
        boolean allowedDebugPlacement = sanctuary.debugEphemeral() && adminOverride;
        if (!sanctuary.ownerId().equals(placerId) && !allowedDebugPlacement) {
            throw new AnchorPlacementException("Only the Sanctuary owner may place this bound Beacon");
        }
        if (sanctuary.state() == SanctuaryState.DESTROYED) {
            throw new AnchorPlacementException("This Sanctuary Beacon was permanently destroyed");
        }
        if (sanctuary.state() != SanctuaryState.INACTIVE) {
            throw new AnchorPlacementException("This Sanctuary is already active");
        }

        spacingService.validatePlacement(
            sanctuary.id(),
            sanctuary.ownerId(),
            newPosition,
            maximumRadius,
            spacingMargin
        );

        Instant now = clock.instant();
        Sanctuary active = copy(
            sanctuary,
            Optional.of(newPosition),
            sanctuary.anchorGeneration(),
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            now
        );
        repository.save(active);
        return active;
    }

    public Optional<Sanctuary> recordDestruction(
        AnchorMetadata metadata,
        String reason
    ) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(reason, "reason");
        if (!metadata.isBound() || reason.isBlank()) {
            return Optional.empty();
        }

        Optional<Sanctuary> result = repository.findById(metadata.anchorId());
        if (result.isEmpty()) {
            return Optional.empty();
        }

        Sanctuary sanctuary = result.orElseThrow();
        if (sanctuary.state() != SanctuaryState.INACTIVE) {
            return Optional.empty();
        }
        if (!matchesPersistentIdentity(sanctuary, metadata)) {
            return Optional.empty();
        }

        if (sanctuary.debugEphemeral()) {
            repository.delete(sanctuary.id());
            return Optional.empty();
        }

        Instant now = clock.instant();
        Sanctuary destroyed = copy(
            sanctuary,
            Optional.empty(),
            sanctuary.anchorGeneration(),
            SanctuaryState.DESTROYED,
            Optional.of(now),
            Optional.of(reason),
            now
        );
        repository.save(destroyed);
        return Optional.of(destroyed);
    }

    public AnchorRecoveryResult recover(
        UUID sanctuaryId,
        UUID ownerId,
        Duration cooldown
    ) throws SQLException, AnchorRecoveryException {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative");
        }

        Sanctuary sanctuary = repository.findById(sanctuaryId)
            .orElseThrow(() -> new AnchorRecoveryException("No Sanctuary exists with that ID"));

        if (sanctuary.debugEphemeral()) {
            throw new AnchorRecoveryException("Ephemeral debug Sanctuaries cannot be recovered");
        }
        if (!sanctuary.ownerId().equals(ownerId)) {
            throw new AnchorRecoveryException("You do not own that Sanctuary");
        }
        if (sanctuary.state() == SanctuaryState.DESTROYED) {
            throw new AnchorRecoveryException(
                "That Sanctuary Beacon was recorded as destroyed and cannot be recovered"
            );
        }
        if (sanctuary.state() != SanctuaryState.INACTIVE) {
            throw new AnchorRecoveryException(
                "That Sanctuary is active. Recovery is only available while its Beacon is inactive"
            );
        }

        Instant now = clock.instant();
        Instant availableAt = sanctuary.updatedAt().plus(cooldown);
        if (now.isBefore(availableAt)) {
            long seconds = Duration.between(now, availableAt).getSeconds();
            throw new AnchorRecoveryException(
                "Beacon recovery is available in " + seconds + " second" + (seconds == 1 ? "" : "s")
            );
        }
        if (sanctuary.anchorGeneration() == Integer.MAX_VALUE) {
            throw new AnchorRecoveryException("This Sanctuary cannot advance to another Beacon generation");
        }

        int nextGeneration = sanctuary.anchorGeneration() + 1;
        Sanctuary recovered = copy(
            sanctuary,
            Optional.empty(),
            nextGeneration,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            now
        );
        repository.save(recovered);

        return new AnchorRecoveryResult(
            recovered,
            new AnchorMetadata(
                recovered.id(),
                Optional.of(recovered.ownerId()),
                recovered.tier(),
                recovered.anchorGeneration()
            )
        );
    }

    private Sanctuary requireMatchingSanctuary(
        AnchorMetadata metadata
    ) throws SQLException, AnchorPlacementException {
        if (!metadata.isBound()) {
            throw new AnchorPlacementException("Sanctuary Beacon is not bound to an owner");
        }

        Sanctuary sanctuary = repository.findById(metadata.anchorId())
            .orElseThrow(() -> new AnchorPlacementException(
                "No registered Sanctuary exists for this bound Beacon"
            ));

        if (!matchesPersistentIdentity(sanctuary, metadata)) {
            if (sanctuary.anchorGeneration() != metadata.generation()) {
                throw new AnchorPlacementException(
                    "This Sanctuary Beacon is stale. A newer recovered copy exists"
                );
            }
            throw new AnchorPlacementException(
                "This Sanctuary Beacon metadata does not match the registered Sanctuary"
            );
        }
        return sanctuary;
    }

    private static boolean matchesPersistentIdentity(
        Sanctuary sanctuary,
        AnchorMetadata metadata
    ) {
        return metadata.ownerId().orElseThrow().equals(sanctuary.ownerId())
            && metadata.tier() == sanctuary.tier()
            && metadata.generation() == sanctuary.anchorGeneration();
    }

    private static Sanctuary copy(
        Sanctuary sanctuary,
        Optional<SanctuaryPosition> position,
        int anchorGeneration,
        SanctuaryState state,
        Optional<Instant> destroyedAt,
        Optional<String> destructionReason,
        Instant updatedAt
    ) {
        return new Sanctuary(
            sanctuary.id(),
            sanctuary.ownerId(),
            sanctuary.type(),
            sanctuary.name(),
            position,
            sanctuary.tier(),
            anchorGeneration,
            sanctuary.territoryRadius(),
            state,
            destroyedAt,
            destructionReason,
            sanctuary.debugEphemeral(),
            sanctuary.createdAt(),
            updatedAt
        );
    }
}

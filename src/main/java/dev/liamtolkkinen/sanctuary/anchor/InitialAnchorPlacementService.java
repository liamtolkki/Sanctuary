package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class InitialAnchorPlacementService {
    private final SanctuaryRepository repository;
    private final Clock clock;

    public InitialAnchorPlacementService(SanctuaryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    InitialAnchorPlacementService(
        SanctuaryRepository repository,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Sanctuary createBeaconSanctuary(
        AnchorMetadata metadata,
        String ownerName,
        SanctuaryPosition position,
        double territoryArea
    ) throws SQLException, AnchorPlacementException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(position, "position");

        if (metadata.ownerId().isEmpty()) {
            throw new AnchorPlacementException("Sanctuary Beacon must be bound before activation");
        }
        if (repository.findById(metadata.anchorId()).isPresent()) {
            throw new AnchorPlacementException("A Sanctuary already exists for this anchor ID");
        }
        if (ownerName.isBlank()) {
            throw new AnchorPlacementException("Owner name must not be blank");
        }
        if (!Double.isFinite(territoryArea) || territoryArea <= 0.0) {
            throw new AnchorPlacementException(
                "Initial territory area must be finite and greater than zero"
            );
        }

        Instant now = clock.instant();
        Sanctuary sanctuary = new Sanctuary(
            metadata.anchorId(),
            metadata.ownerId().orElseThrow(),
            SanctuaryType.BEACON,
            ownerName + "'s Sanctuary",
            Optional.of(position),
            metadata.tier(),
            territoryArea,
            SanctuaryState.ACTIVE,
            now,
            now
        );

        repository.save(sanctuary);
        return sanctuary;
    }
}

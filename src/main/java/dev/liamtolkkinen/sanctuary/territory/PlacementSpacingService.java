package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.anchor.AnchorPlacementException;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class PlacementSpacingService {
    private final SanctuaryRepository repository;

    public PlacementSpacingService(SanctuaryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void validatePlacement(
        UUID sanctuaryId,
        UUID ownerId,
        SanctuaryPosition candidate,
        double maximumRadius,
        double margin
    ) throws SQLException, AnchorPlacementException {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(candidate, "candidate");

        double minimumDistance = TerritoryCalculator.minimumAnchorDistance(
            maximumRadius,
            margin
        );

        for (Sanctuary other : repository.findActiveInWorld(candidate.world())) {
            if (other.id().equals(sanctuaryId) || other.ownerId().equals(ownerId)) {
                continue;
            }
            if (other.position().isEmpty()) {
                continue;
            }

            double distance = TerritoryCalculator.horizontalDistance(
                candidate,
                other.position().orElseThrow()
            );
            if (distance < minimumDistance) {
                throw new AnchorPlacementException(
                    "This Sanctuary Beacon is too close to another owner's Sanctuary. "
                        + "Required anchor distance: "
                        + formatDistance(minimumDistance)
                        + " blocks; actual distance: "
                        + formatDistance(distance)
                        + " blocks."
                );
            }
        }
    }

    private static String formatDistance(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

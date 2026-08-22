package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TerritoryPresenceService {
    public Optional<Sanctuary> findCurrentSanctuary(
        List<Sanctuary> sanctuaries,
        String world,
        double x,
        double z
    ) {
        Objects.requireNonNull(sanctuaries, "sanctuaries");
        Objects.requireNonNull(world, "world");

        return sanctuaries.stream()
            .filter(sanctuary -> sanctuary.state() == SanctuaryState.ACTIVE)
            .filter(sanctuary -> sanctuary.position().isPresent())
            .filter(sanctuary -> TerritoryCalculator.contains(
                sanctuary.position().orElseThrow(),
                sanctuary.territoryRadius(),
                world,
                x,
                z
            ))
            .min(
                Comparator.comparingDouble(
                    (Sanctuary sanctuary) -> distanceSquared(
                        sanctuary.position().orElseThrow(),
                        x,
                        z
                    )
                ).thenComparing(sanctuary -> sanctuary.id().toString())
            );
    }

    private static double distanceSquared(SanctuaryPosition position, double x, double z) {
        double deltaX = x - (position.x() + 0.5);
        double deltaZ = z - (position.z() + 0.5);
        return deltaX * deltaX + deltaZ * deltaZ;
    }
}

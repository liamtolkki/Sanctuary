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
        double y,
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
                y,
                z
            ))
            .min(
                Comparator.comparingDouble(
                    (Sanctuary sanctuary) -> TerritoryCalculator.scaledDistanceSquared(
                        sanctuary.position().orElseThrow(),
                        x,
                        y,
                        z
                    )
                ).thenComparing(sanctuary -> sanctuary.id().toString())
            );
    }

    public Optional<Sanctuary> findCurrentSanctuary(
        List<Sanctuary> sanctuaries,
        String world,
        double x,
        double z
    ) {
        return sanctuaries.stream()
            .filter(sanctuary -> sanctuary.position().isPresent())
            .findFirst()
            .flatMap(sanctuary -> findCurrentSanctuary(
                sanctuaries,
                world,
                x,
                sanctuary.position().orElseThrow().y() + 0.5,
                z
            ));
    }
}

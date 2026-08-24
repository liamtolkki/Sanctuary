package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AnchorTerritoryService {
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuaryAnchorRepository anchorRepository;

    public AnchorTerritoryService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository
    ) {
        this.sanctuaryRepository = Objects.requireNonNull(sanctuaryRepository, "sanctuaryRepository");
        this.anchorRepository = Objects.requireNonNull(anchorRepository, "anchorRepository");
    }

    public Optional<Sanctuary> findCurrentSanctuary(
        String world,
        double x,
        double z
    ) throws SQLException {
        Optional<SanctuaryAnchor> nearest = coveringAnchors(world, x, z).stream()
            .min(Comparator
                .comparingDouble((SanctuaryAnchor anchor) -> distance(anchor, x, z))
                .thenComparing(anchor -> anchor.id().toString()));
        if (nearest.isEmpty()) {
            return Optional.empty();
        }
        return sanctuaryRepository.findById(nearest.orElseThrow().sanctuaryId());
    }

    public List<SanctuaryAnchor> coveringAnchors(
        String world,
        double x,
        double z
    ) throws SQLException {
        Objects.requireNonNull(world, "world");
        return anchorRepository.findActiveInWorld(world).stream()
            .filter(anchor -> anchor.position().isPresent())
            .filter(anchor -> TerritoryCalculator.contains(
                anchor.position().orElseThrow(),
                anchor.territoryRadius(),
                world,
                x,
                z
            ))
            .toList();
    }

    public List<SanctuaryAnchor> coveringAnchors(
        UUID sanctuaryId,
        String world,
        double x,
        double z
    ) throws SQLException {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(world, "world");
        return activeAnchors(sanctuaryId).stream()
            .filter(anchor -> TerritoryCalculator.contains(
                anchor.position().orElseThrow(),
                anchor.territoryRadius(),
                world,
                x,
                z
            ))
            .toList();
    }

    public boolean contains(
        UUID sanctuaryId,
        String world,
        double x,
        double z
    ) throws SQLException {
        return !coveringAnchors(sanctuaryId, world, x, z).isEmpty();
    }

    public List<SanctuaryAnchor> activeAnchors(UUID sanctuaryId) throws SQLException {
        return anchorRepository.findBySanctuary(sanctuaryId).stream()
            .filter(anchor -> anchor.state() == dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState.ACTIVE)
            .filter(anchor -> anchor.position().isPresent())
            .toList();
    }

    private static double distance(SanctuaryAnchor anchor, double x, double z) {
        var position = anchor.position().orElseThrow();
        return Math.hypot(x - (position.x() + 0.5), z - (position.z() + 0.5));
    }
}

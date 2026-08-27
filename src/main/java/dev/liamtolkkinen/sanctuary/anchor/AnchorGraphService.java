package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import dev.liamtolkkinen.sanctuary.upgrade.SanctuaryUpgradeType;
import dev.liamtolkkinen.sanctuary.upgrade.UpgradeRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AnchorGraphService {
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuaryAnchorRepository anchorRepository;
    private final UpgradeRepository upgradeRepository;
    private final Clock clock;

    public AnchorGraphService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository
    ) {
        this(sanctuaryRepository, anchorRepository, null, Clock.systemUTC());
    }

    public AnchorGraphService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        UpgradeRepository upgradeRepository
    ) {
        this(sanctuaryRepository, anchorRepository, upgradeRepository, Clock.systemUTC());
    }

    AnchorGraphService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        Clock clock
    ) {
        this(sanctuaryRepository, anchorRepository, null, clock);
    }

    AnchorGraphService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        UpgradeRepository upgradeRepository,
        Clock clock
    ) {
        this.sanctuaryRepository = Objects.requireNonNull(sanctuaryRepository, "sanctuaryRepository");
        this.anchorRepository = Objects.requireNonNull(anchorRepository, "anchorRepository");
        this.upgradeRepository = upgradeRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean isRegisteredAnchor(UUID anchorId) throws SQLException {
        return anchorRepository.findById(anchorId).isPresent();
    }

    public AnchorPlacementOutcome placeNew(
        AnchorMetadata metadata,
        SanctuaryType type,
        UUID ownerId,
        String ownerName,
        SanctuaryPosition position,
        double initialRadius,
        double maximumRadius,
        double spacingMargin
    ) throws SQLException, AnchorPlacementException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(position, "position");
        if (!metadata.isBound() || !metadata.ownerId().orElseThrow().equals(ownerId)) {
            throw new AnchorPlacementException("Sanctuary anchor must be bound to the placing owner");
        }
        if (anchorRepository.findById(metadata.anchorId()).isPresent()) {
            throw new AnchorPlacementException("A Sanctuary anchor already exists with this ID");
        }

        double currentRadius = AnchorTierProgression.radiusForTier(maximumRadius, metadata.tier());
        List<SanctuaryAnchor> neighbors = joinCandidates(
            ownerId,
            position,
            maximumRadius,
            Optional.empty()
        );
        Instant now = clock.instant();

        if (!neighbors.isEmpty()) {
            UUID sanctuaryId = requireSingleJoinSanctuary(neighbors);
            requireExtensionUnlocked(sanctuaryId);
            Sanctuary sanctuary = requireSanctuary(sanctuaryId);
            SanctuaryAnchor anchor = new SanctuaryAnchor(
                metadata.anchorId(),
                sanctuary.id(),
                Optional.empty(),
                type,
                Optional.of(position),
                metadata.tier(),
                metadata.generation(),
                currentRadius,
                SanctuaryState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                now,
                now
            );
            anchorRepository.save(anchor);
            connectToNeighbors(anchor.id(), neighbors);
            return new AnchorPlacementOutcome(sanctuary, anchor, true, false);
        }

        validateIndependentPlacement(position, maximumRadius, spacingMargin, Optional.empty());
        if (ownerName.isBlank()) {
            throw new AnchorPlacementException("Owner name must not be blank");
        }

        UUID sanctuaryId = metadata.anchorId();
        Sanctuary sanctuary = createFreshSanctuary(
            sanctuaryId,
            ownerId,
            ownerName,
            type,
            position,
            metadata.tier(),
            metadata.generation(),
            currentRadius,
            now
        );
        sanctuaryRepository.save(sanctuary);

        SanctuaryAnchor anchor = new SanctuaryAnchor(
            metadata.anchorId(),
            sanctuary.id(),
            Optional.empty(),
            type,
            Optional.of(position),
            metadata.tier(),
            metadata.generation(),
            currentRadius,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            now,
            now
        );
        try {
            anchorRepository.save(anchor);
        } catch (SQLException exception) {
            sanctuaryRepository.delete(sanctuary.id());
            throw exception;
        }
        return new AnchorPlacementOutcome(sanctuary, anchor, false, false);
    }

    public AnchorPlacementOutcome placeBound(
        AnchorMetadata metadata,
        SanctuaryType itemType,
        UUID placerId,
        String placerName,
        SanctuaryPosition position,
        double maximumRadius,
        double spacingMargin,
        boolean adminOverride
    ) throws SQLException, AnchorPlacementException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(itemType, "itemType");
        Objects.requireNonNull(placerId, "placerId");
        Objects.requireNonNull(position, "position");

        SanctuaryAnchor existing = requireMatchingAnchor(metadata);
        Sanctuary source = requireSanctuary(existing.sanctuaryId());
        if (source.debugEphemeral() && !adminOverride) {
            throw new AnchorPlacementException("Only an administrator may place an ephemeral debug anchor");
        }
        if (existing.type() != itemType) {
            throw new AnchorPlacementException("This anchor item type does not match its registered anchor");
        }
        if (existing.state() == SanctuaryState.DESTROYED) {
            throw new AnchorPlacementException("This Sanctuary anchor was permanently destroyed");
        }
        if (existing.state() != SanctuaryState.INACTIVE) {
            throw new AnchorPlacementException("This Sanctuary anchor is already active");
        }

        // Anchor item provenance is intentionally ignored here. The placer determines which
        // Sanctuary the physical anchor joins, while the anchor keeps its own identity/upgrades.
        List<SanctuaryAnchor> neighbors = joinCandidates(
            placerId,
            position,
            maximumRadius,
            Optional.of(existing.id())
        );
        Instant now = clock.instant();

        if (!neighbors.isEmpty()) {
            UUID targetSanctuaryId = requireSingleJoinSanctuary(neighbors);
            requireExtensionUnlocked(targetSanctuaryId);
            Sanctuary target = requireSanctuary(targetSanctuaryId);
            boolean movingSanctuaries = !target.id().equals(source.id());
            anchorRepository.deleteEdgesForAnchor(existing.id());
            SanctuaryAnchor activated = copyAnchor(
                existing,
                target.id(),
                Optional.empty(),
                Optional.of(position),
                SanctuaryState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                now
            );
            anchorRepository.save(activated);
            connectToNeighbors(activated.id(), neighbors);

            boolean deletedSource = false;
            if (movingSanctuaries && remainingNonDestroyed(source.id(), existing.id()).isEmpty()) {
                sanctuaryRepository.delete(source.id());
                deletedSource = true;
            }
            return new AnchorPlacementOutcome(target, activated, true, deletedSource);
        }

        validateIndependentPlacement(
            position,
            maximumRadius,
            spacingMargin,
            Optional.of(existing.id())
        );

        // A detached/re-homed physical anchor always starts a fresh Sanctuary. This deliberately
        // prevents trust, blacklist, lockdown, naming, sentry defaults, or other shared state from
        // following the item. Only the physical anchor's own upgrades persist.
        String ownerName = placerName == null || placerName.isBlank() ? "Owner" : placerName;
        UUID newSanctuaryId = UUID.randomUUID();
        Sanctuary newSanctuary = createFreshSanctuary(
            newSanctuaryId,
            placerId,
            ownerName,
            existing.type(),
            position,
            existing.tier(),
            existing.generation(),
            existing.territoryRadius(),
            now
        );
        sanctuaryRepository.save(newSanctuary);

        anchorRepository.deleteEdgesForAnchor(existing.id());
        SanctuaryAnchor activated = copyAnchor(
            existing,
            newSanctuaryId,
            Optional.empty(),
            Optional.of(position),
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            now
        );
        try {
            anchorRepository.save(activated);
        } catch (SQLException exception) {
            sanctuaryRepository.delete(newSanctuaryId);
            throw exception;
        }

        boolean deletedSource = false;
        if (remainingNonDestroyed(source.id(), existing.id()).isEmpty()) {
            sanctuaryRepository.delete(source.id());
            deletedSource = true;
        }
        return new AnchorPlacementOutcome(newSanctuary, activated, false, deletedSource);
    }

    public GraphAnchorBreakResult breakAnchor(
        AnchorMetadata metadata,
        UUID breakerId,
        SanctuaryPosition currentPosition,
        boolean adminOverride
    ) throws SQLException, AnchorPlacementException {
        SanctuaryAnchor anchor = requireMatchingAnchor(metadata);
        Sanctuary sanctuary = requireSanctuary(anchor.sanctuaryId());
        if (!adminOverride && !sanctuary.ownerId().equals(breakerId)) {
            throw new AnchorPlacementException("Only the Sanctuary owner may break this anchor");
        }
        requireActiveAt(anchor, currentPosition);
        requireRemovable(anchor.id());
        return deactivate(anchor, sanctuary);
    }

    public GraphAnchorBreakResult breakAnchorFromEnvironment(
        AnchorMetadata metadata,
        SanctuaryPosition currentPosition
    ) throws SQLException, AnchorPlacementException {
        SanctuaryAnchor anchor = requireMatchingAnchor(metadata);
        requireActiveAt(anchor, currentPosition);
        requireRemovable(anchor.id());
        return deactivate(anchor, requireSanctuary(anchor.sanctuaryId()));
    }

    public Optional<SanctuaryAnchor> recordDestruction(
        AnchorMetadata metadata,
        String reason
    ) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(reason, "reason");
        SanctuaryAnchor anchor = anchorRepository.findById(metadata.anchorId()).orElse(null);
        if (anchor == null || anchor.state() != SanctuaryState.INACTIVE || reason.isBlank()) {
            return Optional.empty();
        }
        if (!matches(anchor, metadata)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        anchorRepository.deleteEdgesForAnchor(anchor.id());
        SanctuaryAnchor destroyed = copyAnchor(
            anchor,
            anchor.sanctuaryId(),
            Optional.empty(),
            Optional.empty(),
            SanctuaryState.DESTROYED,
            Optional.of(now),
            Optional.of(reason),
            now
        );
        anchorRepository.save(destroyed);

        Sanctuary sanctuary = sanctuaryRepository.findById(anchor.sanctuaryId()).orElse(null);
        if (sanctuary != null && remainingNonDestroyed(anchor.sanctuaryId(), anchor.id()).isEmpty()) {
            sanctuaryRepository.save(new Sanctuary(
                sanctuary.id(), sanctuary.ownerId(), sanctuary.type(), sanctuary.name(), Optional.empty(),
                sanctuary.tier(), sanctuary.anchorGeneration(), sanctuary.territoryRadius(),
                SanctuaryState.DESTROYED, Optional.of(now), Optional.of(reason), sanctuary.debugEphemeral(),
                sanctuary.createdAt(), now
            ));
        }
        return Optional.of(destroyed);
    }

    /**
     * Compatibility name retained for callers/tests. In the undirected graph model this
     * means "removable without disconnecting the remaining active graph", not tree leaf.
     */
    public SanctuaryAnchor requireLeaf(UUID anchorId) throws SQLException, AnchorPlacementException {
        return requireRemovable(anchorId);
    }

    public SanctuaryAnchor requireRemovable(UUID anchorId) throws SQLException, AnchorPlacementException {
        SanctuaryAnchor anchor = anchorRepository.findById(anchorId)
            .orElseThrow(() -> new AnchorPlacementException("No registered Sanctuary anchor exists with this ID"));
        if (anchor.state() != SanctuaryState.ACTIVE) {
            return anchor;
        }

        List<SanctuaryAnchor> remaining = anchorRepository.findBySanctuary(anchor.sanctuaryId()).stream()
            .filter(value -> value.state() == SanctuaryState.ACTIVE)
            .filter(value -> !value.id().equals(anchorId))
            .toList();
        if (remaining.size() <= 1) {
            return anchor;
        }

        Set<UUID> remainingIds = new HashSet<>();
        for (SanctuaryAnchor value : remaining) {
            remainingIds.add(value.id());
        }

        UUID start = remaining.getFirst().id();
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            for (UUID neighbor : anchorRepository.findNeighborIds(current)) {
                if (neighbor.equals(anchorId) || !remainingIds.contains(neighbor) || !visited.add(neighbor)) {
                    continue;
                }
                queue.addLast(neighbor);
            }
        }

        if (visited.size() != remainingIds.size()) {
            throw new AnchorPlacementException(
                "This anchor cannot be removed because doing so would disconnect the Sanctuary anchor graph."
            );
        }
        return anchor;
    }

    public Optional<SanctuaryAnchor> nearestJoinParent(
        UUID ownerId,
        SanctuaryPosition candidate,
        double maximumRadius,
        Optional<UUID> excludedAnchorId
    ) throws SQLException {
        return joinCandidates(ownerId, candidate, maximumRadius, excludedAnchorId).stream()
            .min(java.util.Comparator
                .comparingDouble((SanctuaryAnchor anchor) -> TerritoryCalculator.horizontalDistance(
                    candidate,
                    anchor.position().orElseThrow()
                ))
                .thenComparing(anchor -> anchor.id().toString()));
    }

    public void validateIndependentPlacement(
        SanctuaryPosition candidate,
        double maximumRadius,
        double spacingMargin,
        Optional<UUID> excludedAnchorId
    ) throws SQLException, AnchorPlacementException {
        double minimumDistance = TerritoryCalculator.minimumAnchorDistance(maximumRadius, spacingMargin);
        for (SanctuaryAnchor other : anchorRepository.findActiveInWorld(candidate.world())) {
            if (excludedAnchorId.isPresent() && other.id().equals(excludedAnchorId.orElseThrow())) {
                continue;
            }
            if (other.position().isEmpty()) {
                continue;
            }
            double distance = TerritoryCalculator.horizontalDistance(candidate, other.position().orElseThrow());
            if (distance < minimumDistance) {
                throw new AnchorPlacementException(
                    "This anchor is too close to an independent Sanctuary. Required anchor distance: "
                        + formatDistance(minimumDistance) + " blocks; actual distance: "
                        + formatDistance(distance) + " blocks."
                );
            }
        }
    }

    private List<SanctuaryAnchor> joinCandidates(
        UUID ownerId,
        SanctuaryPosition candidate,
        double maximumRadius,
        Optional<UUID> excludedAnchorId
    ) throws SQLException {
        java.util.ArrayList<SanctuaryAnchor> result = new java.util.ArrayList<>();
        for (SanctuaryAnchor anchor : anchorRepository.findActiveInWorld(candidate.world())) {
            if (excludedAnchorId.isPresent() && anchor.id().equals(excludedAnchorId.orElseThrow())) {
                continue;
            }
            if (anchor.position().isEmpty()) {
                continue;
            }
            Sanctuary sanctuary = sanctuaryRepository.findById(anchor.sanctuaryId()).orElse(null);
            if (sanctuary == null || !sanctuary.ownerId().equals(ownerId)) {
                continue;
            }
            double distance = TerritoryCalculator.horizontalDistance(candidate, anchor.position().orElseThrow());
            if (distance <= anchor.territoryRadius()) {
                result.add(anchor);
            }
        }
        return List.copyOf(result);
    }

    private UUID requireSingleJoinSanctuary(List<SanctuaryAnchor> neighbors)
        throws AnchorPlacementException {
        UUID sanctuaryId = neighbors.getFirst().sanctuaryId();
        boolean ambiguous = neighbors.stream().anyMatch(anchor -> !anchor.sanctuaryId().equals(sanctuaryId));
        if (ambiguous) {
            throw new AnchorPlacementException(
                "This anchor is within joining range of multiple separate Sanctuaries. Move it before placing."
            );
        }
        return sanctuaryId;
    }

    private void requireExtensionUnlocked(UUID sanctuaryId)
        throws SQLException, AnchorPlacementException {
        if (upgradeRepository == null) {
            return;
        }
        if (!upgradeRepository.hasSanctuaryUpgrade(
            sanctuaryId,
            SanctuaryUpgradeType.TERRITORY_KEYSTONE
        )) {
            throw new AnchorPlacementException(
                "This Sanctuary needs a Territory Keystone before another anchor can extend it."
            );
        }
    }

    private void connectToNeighbors(UUID anchorId, List<SanctuaryAnchor> neighbors) throws SQLException {
        for (SanctuaryAnchor neighbor : neighbors) {
            anchorRepository.saveEdge(anchorId, neighbor.id());
        }
    }

    private GraphAnchorBreakResult deactivate(
        SanctuaryAnchor anchor,
        Sanctuary sanctuary
    ) throws SQLException, AnchorPlacementException {
        if (anchor.generation() == Integer.MAX_VALUE) {
            throw new AnchorPlacementException("This anchor cannot advance to another generation");
        }
        Instant now = clock.instant();
        anchorRepository.deleteEdgesForAnchor(anchor.id());

        if (sanctuary.debugEphemeral()) {
            SanctuaryAnchor destroyed = copyAnchor(
                anchor,
                anchor.sanctuaryId(),
                Optional.empty(),
                Optional.empty(),
                SanctuaryState.DESTROYED,
                Optional.of(now),
                Optional.of("DEBUG_ANCHOR_REMOVED"),
                now
            );
            anchorRepository.save(destroyed);
            Sanctuary destroyedSanctuary = new Sanctuary(
                sanctuary.id(), sanctuary.ownerId(), sanctuary.type(), sanctuary.name(), Optional.empty(),
                sanctuary.tier(), sanctuary.anchorGeneration(), sanctuary.territoryRadius(),
                SanctuaryState.DESTROYED, Optional.of(now), Optional.of("DEBUG_ANCHOR_REMOVED"), true,
                sanctuary.createdAt(), now
            );
            sanctuaryRepository.save(destroyedSanctuary);
            return new GraphAnchorBreakResult(destroyedSanctuary, destroyed, true, true);
        }

        SanctuaryAnchor inactive = new SanctuaryAnchor(
            anchor.id(), anchor.sanctuaryId(), Optional.empty(), anchor.type(), Optional.empty(),
            anchor.tier(), anchor.generation() + 1, anchor.territoryRadius(), SanctuaryState.INACTIVE,
            Optional.empty(), Optional.empty(), anchor.createdAt(), now
        );
        anchorRepository.save(inactive);

        boolean anyActive = anchorRepository.findBySanctuary(sanctuary.id()).stream()
            .anyMatch(value -> value.state() == SanctuaryState.ACTIVE);
        Sanctuary updatedSanctuary = sanctuary;
        if (!anyActive) {
            updatedSanctuary = new Sanctuary(
                sanctuary.id(), sanctuary.ownerId(), sanctuary.type(), sanctuary.name(), Optional.empty(),
                sanctuary.tier(), inactive.generation(), sanctuary.territoryRadius(), SanctuaryState.INACTIVE,
                Optional.empty(), Optional.empty(), sanctuary.debugEphemeral(), sanctuary.createdAt(), now
            );
            sanctuaryRepository.save(updatedSanctuary);
        }
        return new GraphAnchorBreakResult(updatedSanctuary, inactive, !anyActive, false);
    }

    private SanctuaryAnchor requireMatchingAnchor(AnchorMetadata metadata)
        throws SQLException, AnchorPlacementException {
        SanctuaryAnchor anchor = anchorRepository.findById(metadata.anchorId())
            .orElseThrow(() -> new AnchorPlacementException("No registered Sanctuary exists for this bound anchor"));
        if (!matches(anchor, metadata)) {
            if (anchor.generation() != metadata.generation()) {
                throw new AnchorPlacementException("This Sanctuary anchor is stale. A newer recovered copy exists");
            }
            throw new AnchorPlacementException("This Sanctuary anchor metadata does not match the registered anchor");
        }
        return anchor;
    }

    private boolean matches(SanctuaryAnchor anchor, AnchorMetadata metadata) {
        // Owner UUID is intentionally not part of physical anchor identity. An anchor may be
        // traded, gifted, stolen, or otherwise re-homed by whoever actually places the item.
        return metadata.tier() == anchor.tier()
            && metadata.generation() == anchor.generation();
    }

    private static void requireActiveAt(SanctuaryAnchor anchor, SanctuaryPosition position)
        throws AnchorPlacementException {
        if (anchor.state() != SanctuaryState.ACTIVE) {
            throw new AnchorPlacementException("This Sanctuary anchor is not currently active");
        }
        if (!anchor.position().equals(Optional.of(position))) {
            throw new AnchorPlacementException("This anchor does not match its registered Sanctuary location");
        }
    }

    private Sanctuary requireSanctuary(UUID id) throws SQLException, AnchorPlacementException {
        return sanctuaryRepository.findById(id)
            .orElseThrow(() -> new AnchorPlacementException("No Sanctuary exists for this anchor graph"));
    }

    private List<SanctuaryAnchor> remainingNonDestroyed(UUID sanctuaryId, UUID excludedAnchorId)
        throws SQLException {
        return anchorRepository.findBySanctuary(sanctuaryId).stream()
            .filter(anchor -> !anchor.id().equals(excludedAnchorId))
            .filter(anchor -> anchor.state() != SanctuaryState.DESTROYED)
            .toList();
    }

    private static Sanctuary createFreshSanctuary(
        UUID sanctuaryId,
        UUID ownerId,
        String ownerName,
        SanctuaryType type,
        SanctuaryPosition position,
        int tier,
        int generation,
        double territoryRadius,
        Instant now
    ) {
        return new Sanctuary(
            sanctuaryId,
            ownerId,
            type,
            ownerName + "'s Sanctuary",
            Optional.of(position),
            tier,
            generation,
            territoryRadius,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }

    private static SanctuaryAnchor copyAnchor(
        SanctuaryAnchor anchor,
        UUID sanctuaryId,
        Optional<UUID> parentAnchorId,
        Optional<SanctuaryPosition> position,
        SanctuaryState state,
        Optional<Instant> destroyedAt,
        Optional<String> destructionReason,
        Instant updatedAt
    ) {
        return new SanctuaryAnchor(
            anchor.id(), sanctuaryId, parentAnchorId, anchor.type(), position,
            anchor.tier(), anchor.generation(), anchor.territoryRadius(), state,
            destroyedAt, destructionReason, anchor.createdAt(), updatedAt
        );
    }

    private static String formatDistance(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

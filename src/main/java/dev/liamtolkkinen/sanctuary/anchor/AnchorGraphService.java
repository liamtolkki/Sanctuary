package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AnchorGraphService {
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuaryAnchorRepository anchorRepository;
    private final Clock clock;

    public AnchorGraphService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository
    ) {
        this(sanctuaryRepository, anchorRepository, Clock.systemUTC());
    }

    AnchorGraphService(
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        Clock clock
    ) {
        this.sanctuaryRepository = Objects.requireNonNull(sanctuaryRepository, "sanctuaryRepository");
        this.anchorRepository = Objects.requireNonNull(anchorRepository, "anchorRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
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

        Optional<SanctuaryAnchor> parent = nearestJoinParent(
            ownerId,
            position,
            maximumRadius,
            Optional.empty()
        );
        Instant now = clock.instant();

        if (parent.isPresent()) {
            SanctuaryAnchor parentAnchor = parent.orElseThrow();
            Sanctuary sanctuary = requireSanctuary(parentAnchor.sanctuaryId());
            SanctuaryAnchor anchor = new SanctuaryAnchor(
                metadata.anchorId(),
                sanctuary.id(),
                Optional.of(parentAnchor.id()),
                type,
                Optional.of(position),
                metadata.tier(),
                metadata.generation(),
                initialRadius,
                SanctuaryState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                now,
                now
            );
            anchorRepository.save(anchor);
            return new AnchorPlacementOutcome(sanctuary, anchor, true, false);
        }

        validateIndependentPlacement(position, maximumRadius, spacingMargin, Optional.empty());
        if (ownerName.isBlank()) {
            throw new AnchorPlacementException("Owner name must not be blank");
        }

        UUID sanctuaryId = metadata.anchorId();
        Sanctuary sanctuary = new Sanctuary(
            sanctuaryId,
            ownerId,
            type,
            ownerName + "'s Sanctuary",
            Optional.of(position),
            metadata.tier(),
            metadata.generation(),
            initialRadius,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
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
            initialRadius,
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
        boolean debugOverride = source.debugEphemeral() && adminOverride;
        if (!source.ownerId().equals(placerId) && !debugOverride) {
            throw new AnchorPlacementException("Only the Sanctuary owner may place this bound anchor");
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
        requireLeaf(existing.id());

        Optional<SanctuaryAnchor> parent = nearestJoinParent(
            source.ownerId(),
            position,
            maximumRadius,
            Optional.of(existing.id())
        );
        Instant now = clock.instant();

        if (parent.isPresent()) {
            SanctuaryAnchor parentAnchor = parent.orElseThrow();
            Sanctuary target = requireSanctuary(parentAnchor.sanctuaryId());
            boolean movingSanctuaries = !target.id().equals(source.id());
            SanctuaryAnchor activated = copyAnchor(
                existing,
                target.id(),
                Optional.of(parentAnchor.id()),
                Optional.of(position),
                SanctuaryState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                now
            );
            anchorRepository.save(activated);

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

        List<SanctuaryAnchor> sourceRemainder = remainingNonDestroyed(source.id(), existing.id());
        if (sourceRemainder.isEmpty()) {
            SanctuaryAnchor activated = copyAnchor(
                existing,
                source.id(),
                Optional.empty(),
                Optional.of(position),
                SanctuaryState.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                now
            );
            anchorRepository.save(activated);
            sanctuaryRepository.save(copySanctuaryRoot(source, activated, now));
            return new AnchorPlacementOutcome(source, activated, false, false);
        }

        UUID newSanctuaryId = UUID.randomUUID();
        String ownerName = placerName == null || placerName.isBlank() ? "Owner" : placerName;
        Sanctuary newSanctuary = new Sanctuary(
            newSanctuaryId,
            source.ownerId(),
            existing.type(),
            ownerName + "'s Sanctuary",
            Optional.of(position),
            existing.tier(),
            existing.generation(),
            existing.territoryRadius(),
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
        sanctuaryRepository.save(newSanctuary);
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
        return new AnchorPlacementOutcome(newSanctuary, activated, false, false);
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
        requireLeaf(anchor.id());
        return deactivate(anchor, sanctuary);
    }

    public GraphAnchorBreakResult breakAnchorFromEnvironment(
        AnchorMetadata metadata,
        SanctuaryPosition currentPosition
    ) throws SQLException, AnchorPlacementException {
        SanctuaryAnchor anchor = requireMatchingAnchor(metadata);
        requireActiveAt(anchor, currentPosition);
        requireLeaf(anchor.id());
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
        SanctuaryAnchor destroyed = copyAnchor(
            anchor,
            anchor.sanctuaryId(),
            anchor.parentAnchorId(),
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

    public SanctuaryAnchor requireLeaf(UUID anchorId) throws SQLException, AnchorPlacementException {
        SanctuaryAnchor anchor = anchorRepository.findById(anchorId)
            .orElseThrow(() -> new AnchorPlacementException("No registered Sanctuary anchor exists with this ID"));
        List<SanctuaryAnchor> children = anchorRepository.findChildren(anchorId);
        if (!children.isEmpty()) {
            throw new AnchorPlacementException(
                "This anchor cannot be removed because " + children.size()
                    + " connected anchor" + (children.size() == 1 ? " depends" : "s depend") + " on it"
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
        return anchorRepository.findActiveInWorld(candidate.world()).stream()
            .filter(anchor -> excludedAnchorId.isEmpty() || !anchor.id().equals(excludedAnchorId.orElseThrow()))
            .filter(anchor -> anchor.position().isPresent())
            .filter(anchor -> sanctuaryRepository.findById(anchor.sanctuaryId())
                .map(sanctuary -> sanctuary.ownerId().equals(ownerId))
                .orElse(false))
            .filter(anchor -> TerritoryCalculator.horizontalDistance(
                candidate,
                anchor.position().orElseThrow()
            ) <= maximumRadius)
            .min(Comparator
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

    private GraphAnchorBreakResult deactivate(
        SanctuaryAnchor anchor,
        Sanctuary sanctuary
    ) throws SQLException, AnchorPlacementException {
        if (anchor.generation() == Integer.MAX_VALUE) {
            throw new AnchorPlacementException("This anchor cannot advance to another generation");
        }
        Instant now = clock.instant();

        if (sanctuary.debugEphemeral()) {
            SanctuaryAnchor destroyed = copyAnchor(
                anchor,
                anchor.sanctuaryId(),
                anchor.parentAnchorId(),
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
            anchor.id(), anchor.sanctuaryId(), anchor.parentAnchorId(), anchor.type(), Optional.empty(),
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
        if (!metadata.isBound()) {
            throw new AnchorPlacementException("Sanctuary anchor is not bound to an owner");
        }
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

    private boolean matches(SanctuaryAnchor anchor, AnchorMetadata metadata) throws SQLException {
        Sanctuary sanctuary = sanctuaryRepository.findById(anchor.sanctuaryId()).orElse(null);
        return sanctuary != null
            && metadata.ownerId().orElseThrow().equals(sanctuary.ownerId())
            && metadata.tier() == anchor.tier()
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

    private static Sanctuary copySanctuaryRoot(
        Sanctuary sanctuary,
        SanctuaryAnchor anchor,
        Instant updatedAt
    ) {
        return new Sanctuary(
            sanctuary.id(), sanctuary.ownerId(), anchor.type(), sanctuary.name(), anchor.position(),
            anchor.tier(), anchor.generation(), anchor.territoryRadius(), anchor.state(),
            Optional.empty(), Optional.empty(), sanctuary.debugEphemeral(), sanctuary.createdAt(), updatedAt
        );
    }

    private static String formatDistance(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

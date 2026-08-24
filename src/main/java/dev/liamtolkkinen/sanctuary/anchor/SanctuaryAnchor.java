package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SanctuaryAnchor(
    UUID id,
    UUID sanctuaryId,
    Optional<UUID> parentAnchorId,
    SanctuaryType type,
    Optional<SanctuaryPosition> position,
    int tier,
    int generation,
    double territoryRadius,
    SanctuaryState state,
    Optional<Instant> destroyedAt,
    Optional<String> destructionReason,
    Instant createdAt,
    Instant updatedAt
) {
    public SanctuaryAnchor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        parentAnchorId = Objects.requireNonNull(parentAnchorId, "parentAnchorId");
        Objects.requireNonNull(type, "type");
        position = Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        destroyedAt = Objects.requireNonNull(destroyedAt, "destroyedAt");
        destructionReason = Objects.requireNonNull(destructionReason, "destructionReason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1");
        }
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be at least 1");
        }
        if (!Double.isFinite(territoryRadius) || territoryRadius <= 0.0) {
            throw new IllegalArgumentException("territoryRadius must be finite and greater than zero");
        }
        if (state == SanctuaryState.ACTIVE && position.isEmpty()) {
            throw new IllegalArgumentException("active anchor must have a position");
        }
        if (state == SanctuaryState.DESTROYED) {
            if (position.isPresent()) {
                throw new IllegalArgumentException("destroyed anchor cannot have a position");
            }
            if (destroyedAt.isEmpty() || destructionReason.isEmpty()) {
                throw new IllegalArgumentException("destroyed anchor must record destruction metadata");
            }
        } else if (destroyedAt.isPresent() || destructionReason.isPresent()) {
            throw new IllegalArgumentException("only destroyed anchors may contain destruction metadata");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
    }

    public boolean isLeaf(Collection<SanctuaryAnchor> anchors) {
        Objects.requireNonNull(anchors, "anchors");
        return anchors.stream().noneMatch(value -> value.parentAnchorId().equals(Optional.of(id)));
    }
}

package dev.liamtolkkinen.sanctuary.sanctuary;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Sanctuary(
    UUID id,
    UUID ownerId,
    SanctuaryType type,
    String name,
    Optional<SanctuaryPosition> position,
    int tier,
    double territoryArea,
    SanctuaryState state,
    Instant createdAt,
    Instant updatedAt
) {
    public Sanctuary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        position = Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1");
        }
        if (!Double.isFinite(territoryArea) || territoryArea <= 0.0) {
            throw new IllegalArgumentException("territoryArea must be finite and greater than zero");
        }
        if (state == SanctuaryState.ACTIVE && position.isEmpty()) {
            throw new IllegalArgumentException("an active Sanctuary must have an anchor position");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }
}

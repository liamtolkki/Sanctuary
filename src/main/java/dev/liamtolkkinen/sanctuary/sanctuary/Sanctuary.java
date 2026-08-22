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
    int anchorGeneration,
    double territoryRadius,
    SanctuaryState state,
    Optional<Instant> destroyedAt,
    Optional<String> destructionReason,
    boolean debugEphemeral,
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
        destroyedAt = Objects.requireNonNull(destroyedAt, "destroyedAt");
        destructionReason = Objects.requireNonNull(destructionReason, "destructionReason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1");
        }
        if (anchorGeneration < 1) {
            throw new IllegalArgumentException("anchorGeneration must be at least 1");
        }
        if (!Double.isFinite(territoryRadius) || territoryRadius <= 0.0) {
            throw new IllegalArgumentException("territoryRadius must be finite and greater than zero");
        }
        if (state == SanctuaryState.ACTIVE && position.isEmpty()) {
            throw new IllegalArgumentException("an active Sanctuary must have an anchor position");
        }
        if (state == SanctuaryState.DESTROYED) {
            if (position.isPresent()) {
                throw new IllegalArgumentException("a destroyed Sanctuary cannot have an anchor position");
            }
            if (destroyedAt.isEmpty() || destructionReason.isEmpty()) {
                throw new IllegalArgumentException(
                    "a destroyed Sanctuary must record when and why its anchor was destroyed"
                );
            }
            if (destructionReason.orElseThrow().isBlank()) {
                throw new IllegalArgumentException("destructionReason must not be blank");
            }
        } else if (destroyedAt.isPresent() || destructionReason.isPresent()) {
            throw new IllegalArgumentException(
                "only a destroyed Sanctuary may contain destruction metadata"
            );
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }
}

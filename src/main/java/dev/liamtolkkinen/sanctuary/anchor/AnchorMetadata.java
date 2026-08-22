package dev.liamtolkkinen.sanctuary.anchor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AnchorMetadata(
    UUID anchorId,
    Optional<UUID> ownerId,
    int tier,
    int generation
) {
    public AnchorMetadata {
        Objects.requireNonNull(anchorId, "anchorId");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");

        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1");
        }
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be at least 1");
        }
    }

    public boolean isBound() {
        return ownerId.isPresent();
    }

    public AnchorMetadata nextGeneration() {
        if (generation == Integer.MAX_VALUE) {
            throw new IllegalStateException("anchor generation cannot advance further");
        }
        return new AnchorMetadata(anchorId, ownerId, tier, generation + 1);
    }

    public AnchorMetadata bind(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (isBound()) {
            throw new IllegalStateException("anchor is already bound");
        }

        return new AnchorMetadata(
            anchorId,
            Optional.of(ownerId),
            tier,
            generation
        );
    }
}

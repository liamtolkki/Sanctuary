package dev.liamtolkkinen.sanctuary.anchor;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AnchorMetadata(
    UUID anchorId,
    Optional<UUID> ownerId,
    int tier
) {
    public AnchorMetadata {
        Objects.requireNonNull(anchorId, "anchorId");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");

        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1");
        }
    }

    public boolean isBound() {
        return ownerId.isPresent();
    }

    public AnchorMetadata bind(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (isBound()) {
            throw new IllegalStateException("anchor is already bound");
        }

        return new AnchorMetadata(
            anchorId,
            Optional.of(ownerId),
            tier
        );
    }
}

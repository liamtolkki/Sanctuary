package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.util.Objects;

public record AnchorRecoveryResult(
    Sanctuary sanctuary,
    AnchorMetadata metadata
) {
    public AnchorRecoveryResult {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(metadata, "metadata");
    }
}

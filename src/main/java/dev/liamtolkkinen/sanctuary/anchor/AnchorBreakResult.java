package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.util.Objects;

public record AnchorBreakResult(
    Sanctuary sanctuary,
    boolean deleted
) {
    public AnchorBreakResult {
        Objects.requireNonNull(sanctuary, "sanctuary");
    }
}

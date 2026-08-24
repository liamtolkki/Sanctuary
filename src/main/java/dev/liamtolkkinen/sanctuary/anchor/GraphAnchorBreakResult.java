package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.util.Objects;

public record GraphAnchorBreakResult(
    Sanctuary sanctuary,
    SanctuaryAnchor anchor,
    boolean sanctuaryInactive,
    boolean deleted
) {
    public GraphAnchorBreakResult {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(anchor, "anchor");
    }
}

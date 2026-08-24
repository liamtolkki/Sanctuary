package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.util.Objects;

public record AnchorPlacementOutcome(
    Sanctuary sanctuary,
    SanctuaryAnchor anchor,
    boolean joinedExistingSanctuary,
    boolean sourceSanctuaryDeleted
) {
    public AnchorPlacementOutcome {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(anchor, "anchor");
    }
}

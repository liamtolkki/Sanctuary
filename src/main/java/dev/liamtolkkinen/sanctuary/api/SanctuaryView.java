package dev.liamtolkkinen.sanctuary.api;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.util.Optional;
import java.util.UUID;

public record SanctuaryView(
    UUID id,
    UUID ownerId,
    SanctuaryType type,
    String name,
    Optional<SanctuaryPositionView> position,
    int tier,
    double territoryRadius,
    SanctuaryState state
) {
}

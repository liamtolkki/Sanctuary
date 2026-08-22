package dev.liamtolkkinen.sanctuary.sanctuary;

import java.util.Objects;

public record SanctuaryPosition(
    String world,
    int x,
    int y,
    int z
) {
    public SanctuaryPosition {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world must not be blank");
        }
    }
}

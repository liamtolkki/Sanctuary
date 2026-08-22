package dev.liamtolkkinen.sanctuary.trust;

import java.util.Locale;

public enum SanctuaryCapability {
    BUILD,
    BREAK,
    INTERACT,
    CONTAINER,
    REDSTONE,
    ENTITIES;

    public static SanctuaryCapability parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

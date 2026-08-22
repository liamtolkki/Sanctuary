package dev.liamtolkkinen.sanctuary.sentry;

public enum SentryOverride {
    INHERIT,
    ENABLED,
    DISABLED;

    public SentryOverride next() {
        return switch (this) {
            case INHERIT -> ENABLED;
            case ENABLED -> DISABLED;
            case DISABLED -> INHERIT;
        };
    }
}

package dev.liamtolkkinen.sanctuary.sentry;

public enum SentryTrigger {
    // Retained only so older persisted rows can still be read. This trigger is permanently disabled
    // and omitted from sentry configuration UI.
    UNAUTHORIZED_PLAYER_ENTERED("Unauthorized Player Entered", false, false),
    CONTAINER_OPENED("Container Opened", false),
    ENTITY_HURT("Entity Hurt", false),
    HOSTILE_MOB_ENTERED("Hostile Mob Present", true),
    NEUTRAL_MOB_ENTERED("Neutral Mob Present", false),
    BEACON_PROXIMITY("Anchor Proximity", false),
    OWNER_ATTACKED("Owner Attacked", true),
    BLOCK_BROKEN("Block Broken", false),
    BLOCK_PLACED("Block Placed", false),
    INTERACTION_USED("Redstone / Interactable Used", false),
    SENTRY_ATTACKED("Sentry Attacked", true),
    BEACON_ATTACKED("Anchor Attacked", true);

    private final String displayName;
    private final boolean defaultEnabled;
    private final boolean configurable;

    SentryTrigger(String displayName, boolean defaultEnabled) {
        this(displayName, defaultEnabled, true);
    }

    SentryTrigger(String displayName, boolean defaultEnabled, boolean configurable) {
        this.displayName = displayName;
        this.defaultEnabled = defaultEnabled;
        this.configurable = configurable;
    }

    public String displayName() {
        return displayName;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    public boolean configurable() {
        return configurable;
    }

    public boolean causesPlayerAggression() {
        return this == OWNER_ATTACKED
            || this == SENTRY_ATTACKED
            || this == BEACON_ATTACKED;
    }
}

package dev.liamtolkkinen.sanctuary.sentry;

public enum SentryTrigger {
    CONTAINER_OPENED("Container Opened", true),
    ENTITY_HURT("Entity Hurt", true),
    HOSTILE_MOB_ENTERED("Hostile Mob Entered", true),
    NEUTRAL_MOB_ENTERED("Neutral Mob Entered", false),
    BEACON_PROXIMITY("Beacon Proximity", false),
    OWNER_ATTACKED("Owner Attacked", true),
    BLOCK_BROKEN("Block Broken", true),
    BLOCK_PLACED("Block Placed", true),
    INTERACTION_USED("Redstone / Interactable Used", true),
    SENTRY_ATTACKED("Sentry Attacked", true),
    BEACON_ATTACKED("Beacon Attacked", true);

    private final String displayName;
    private final boolean defaultEnabled;

    SentryTrigger(String displayName, boolean defaultEnabled) {
        this.displayName = displayName;
        this.defaultEnabled = defaultEnabled;
    }

    public String displayName() { return displayName; }
    public boolean defaultEnabled() { return defaultEnabled; }
}

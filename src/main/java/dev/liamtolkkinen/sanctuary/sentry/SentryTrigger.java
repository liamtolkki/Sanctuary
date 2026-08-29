package dev.liamtolkkinen.sanctuary.sentry;

public enum SentryTrigger {
    UNAUTHORIZED_PLAYER_ENTERED("Unauthorized Player Entered", true),
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

    SentryTrigger(String displayName, boolean defaultEnabled) {
        this.displayName = displayName;
        this.defaultEnabled = defaultEnabled;
    }

    public String displayName() {
        return displayName;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }
}

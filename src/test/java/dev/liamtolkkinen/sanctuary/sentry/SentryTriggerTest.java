package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SentryTriggerTest {
    @Test
    void neutralProtectionDefaultsEnableOnlyCoreDefenseTriggers() {
        assertTrue(SentryTrigger.UNAUTHORIZED_PLAYER_ENTERED.defaultEnabled());
        assertTrue(SentryTrigger.HOSTILE_MOB_ENTERED.defaultEnabled());
        assertTrue(SentryTrigger.OWNER_ATTACKED.defaultEnabled());
        assertTrue(SentryTrigger.SENTRY_ATTACKED.defaultEnabled());
        assertTrue(SentryTrigger.BEACON_ATTACKED.defaultEnabled());

        assertFalse(SentryTrigger.CONTAINER_OPENED.defaultEnabled());
        assertFalse(SentryTrigger.ENTITY_HURT.defaultEnabled());
        assertFalse(SentryTrigger.NEUTRAL_MOB_ENTERED.defaultEnabled());
        assertFalse(SentryTrigger.BEACON_PROXIMITY.defaultEnabled());
        assertFalse(SentryTrigger.BLOCK_BROKEN.defaultEnabled());
        assertFalse(SentryTrigger.BLOCK_PLACED.defaultEnabled());
        assertFalse(SentryTrigger.INTERACTION_USED.defaultEnabled());
    }
}

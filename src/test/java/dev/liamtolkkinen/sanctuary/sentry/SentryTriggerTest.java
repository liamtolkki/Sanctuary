package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class SentryTriggerTest {
    @Test
    void dangerousMobAndDirectAbuseTriggersDefaultOn() {
        assertTrue(SentryTrigger.CONTAINER_OPENED.defaultEnabled());
        assertTrue(SentryTrigger.HOSTILE_MOB_ENTERED.defaultEnabled());
        assertTrue(SentryTrigger.OWNER_ATTACKED.defaultEnabled());
        assertTrue(SentryTrigger.SENTRY_ATTACKED.defaultEnabled());
        assertTrue(SentryTrigger.BEACON_ATTACKED.defaultEnabled());
    }

    @Test
    void broadProximityAndNeutralMobTriggersDefaultOff() {
        assertFalse(SentryTrigger.NEUTRAL_MOB_ENTERED.defaultEnabled());
        assertFalse(SentryTrigger.BEACON_PROXIMITY.defaultEnabled());
    }
}

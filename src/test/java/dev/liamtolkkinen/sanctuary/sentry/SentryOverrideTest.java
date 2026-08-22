package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class SentryOverrideTest {
    @Test
    void overrideCycleReturnsToInherit() {
        assertEquals(SentryOverride.ENABLED, SentryOverride.INHERIT.next());
        assertEquals(SentryOverride.DISABLED, SentryOverride.ENABLED.next());
        assertEquals(SentryOverride.INHERIT, SentryOverride.DISABLED.next());
    }
}

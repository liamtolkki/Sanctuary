package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class SentryDefinitionTest {
    @Test
    void creeperSentryUsesReleasedExtendedItemIdentity() {
        SentryDefinition definition = SentryDefinition.byPersistentId("sentry_creeper").orElseThrow();

        assertEquals(ExtendedItemIds.SENTRY_CREEPER, definition.itemId());
        assertEquals("Creeper Sentry", definition.displayName());
        assertEquals(EntityType.CREEPER, definition.entityType());
        assertEquals(24.0, definition.targetRadius());
        assertTrue(!definition.baby());
    }
}

package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnchorMetadataTest {
    @Test
    void bindPreservesAnchorIdentityAndTier() {
        UUID anchorId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AnchorMetadata unbound = new AnchorMetadata(
            anchorId,
            Optional.empty(),
            1
        );

        AnchorMetadata bound = unbound.bind(ownerId);

        assertFalse(unbound.isBound());
        assertTrue(bound.isBound());
        assertEquals(anchorId, bound.anchorId());
        assertEquals(ownerId, bound.ownerId().orElseThrow());
        assertEquals(1, bound.tier());
    }

    @Test
    void boundAnchorCannotBeClaimedAgain() {
        AnchorMetadata bound = new AnchorMetadata(
            UUID.randomUUID(),
            Optional.of(UUID.randomUUID()),
            1
        );

        assertThrows(
            IllegalStateException.class,
            () -> bound.bind(UUID.randomUUID())
        );
    }

    @Test
    void tierMustBeAtLeastOne() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new AnchorMetadata(
                UUID.randomUUID(),
                Optional.empty(),
                0
            )
        );
    }
}

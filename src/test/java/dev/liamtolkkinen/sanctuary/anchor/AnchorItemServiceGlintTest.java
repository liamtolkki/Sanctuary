package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class AnchorItemServiceGlintTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sanctuaryBeaconHasGlintOverride() {
        var plugin = MockBukkit.createMockPlugin();
        var item = new AnchorItemService(plugin).createUnboundBeacon();
        var meta = item.getItemMeta();

        assertTrue(meta.hasEnchantmentGlintOverride());
        assertTrue(meta.getEnchantmentGlintOverride());
    }

    @Test
    void sanctuaryConduitIsCreatedAsAnUnboundConduitAnchor() {
        var plugin = MockBukkit.createMockPlugin();
        var service = new AnchorItemService(plugin);
        var item = service.createUnboundConduit();
        var metadata = service.readAnchor(item).orElseThrow();

        assertEquals(SanctuaryType.CONDUIT, service.anchorType(item).orElseThrow());
        assertTrue(metadata.ownerId().isEmpty());
        assertEquals(1, metadata.tier());
        assertEquals(1, metadata.generation());
        assertTrue(item.getItemMeta().hasEnchantmentGlintOverride());
        assertTrue(item.getItemMeta().getEnchantmentGlintOverride());
    }
}

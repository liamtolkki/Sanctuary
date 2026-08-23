package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

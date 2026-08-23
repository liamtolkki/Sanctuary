package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SanctuaryProgressionDebugCommandTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testStacksUseAuthoritativeExtendedItems() {
        List<ItemStack> stacks = SanctuaryProgressionDebugCommand.createTestStacks();

        assertEquals(2, stacks.size());

        ItemStack fragments = stacks.get(0);
        assertEquals(Material.SMALL_AMETHYST_BUD, fragments.getType());
        assertEquals(64, fragments.getAmount());
        assertTrue(ExtendedItems.is(
            fragments,
            ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT
        ));

        ItemStack shards = stacks.get(1);
        assertEquals(Material.AMETHYST_SHARD, shards.getType());
        assertEquals(64, shards.getAmount());
        assertTrue(ExtendedItems.is(
            shards,
            ExtendedItemIds.CONSECRATED_SHARD
        ));
    }
}

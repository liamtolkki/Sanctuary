package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.altar.OfferingCatalog;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SanctuaryRelicDebugCommandTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void debugItemsContainEveryOfferingAndFinalRelic() {
        List<ItemStack> items = SanctuaryRelicDebugCommand.createTestItems();
        assertEquals(13, items.size());
        for (int index = 0; index < OfferingCatalog.all().size(); index++) {
            assertTrue(ExtendedItems.is(items.get(index), OfferingCatalog.all().get(index).itemId()));
        }
        assertEquals(Material.SHULKER_SHELL, items.get(8).getType());
        assertTrue(ExtendedItems.is(items.getLast(), ExtendedItemIds.DIVINE_RELIC));
    }
}

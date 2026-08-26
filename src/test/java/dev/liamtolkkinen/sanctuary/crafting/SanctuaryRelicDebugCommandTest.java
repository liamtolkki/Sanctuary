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
            var ingredient = OfferingCatalog.all().get(index).ingredient();
            ItemStack item = items.get(index);
            if (ingredient.extendedItem() != null) {
                assertTrue(ExtendedItems.is(item, ingredient.extendedItem()));
            } else {
                assertEquals(ingredient.material(), item.getType());
            }
        }

        assertEquals(Material.GOLDEN_APPLE, items.get(2).getType());
        assertTrue(ExtendedItems.is(items.getLast(), ExtendedItemIds.DIVINE_RELIC));
    }
}

package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SanctuaryItemUsageGuardTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void extendedItemsAreDetectedInsideOtherwiseVanillaCraftingMatrices() {
        ItemStack[] matrix = new ItemStack[] {
            new ItemStack(Material.GLASS),
            ExtendedItems.create(ExtendedItemIds.SANCTUARY_CORE),
            new ItemStack(Material.OBSIDIAN)
        };

        assertTrue(SanctuaryItemUsageGuard.containsExtendedItem(matrix));

        matrix[1] = new ItemStack(Material.NETHER_STAR);
        assertFalse(SanctuaryItemUsageGuard.containsExtendedItem(matrix));
    }

    @Test
    void progressionArtifactsCannotBePlacedAsVanillaBlocks() {
        assertFalse(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.TERRITORY_KEYSTONE
        ));
        assertFalse(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.WARD_STONE
        ));
        assertFalse(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.BLAST_WARD
        ));
        assertFalse(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.SEAL_OF_KEEPING
        ));
        assertFalse(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.CONSECRATED_KEYSTONE
        ));
        assertFalse(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.SANCTUARY_CONDUIT
        ));
    }

    @Test
    void explicitlyHandledPlaceableItemsRemainAllowed() {
        assertTrue(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.SANCTUARY_BEACON
        ));
        assertTrue(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.DIVINE_ALTAR
        ));
        assertTrue(SanctuaryItemUsageGuard.isAllowedPlacedItem(
            ExtendedItemIds.SENTRY_IRON_GOLEM
        ));
    }
}

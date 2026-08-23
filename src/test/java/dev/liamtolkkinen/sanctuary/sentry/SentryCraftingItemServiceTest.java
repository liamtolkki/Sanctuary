package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SentryCraftingItemServiceTest {
    private SentryCraftingItemService service;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        service = new SentryCraftingItemService(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ominousBannerRequiresTheVanillaEightPatternDesign() {
        ItemStack ominous = service.createSpecialIngredient(
            SentryRecipeCatalog.SpecialIngredient.OMINOUS_BANNER
        );

        assertTrue(service.matchesSpecialIngredient(
            ominous,
            SentryRecipeCatalog.SpecialIngredient.OMINOUS_BANNER
        ));
        assertFalse(service.matchesSpecialIngredient(
            new ItemStack(Material.WHITE_BANNER),
            SentryRecipeCatalog.SpecialIngredient.OMINOUS_BANNER
        ));
    }

    @Test
    void speedTwoPotionRequiresStrongSwiftness() {
        ItemStack speedTwo = service.createSpecialIngredient(
            SentryRecipeCatalog.SpecialIngredient.SPEED_II_POTION
        );

        assertTrue(service.matchesSpecialIngredient(
            speedTwo,
            SentryRecipeCatalog.SpecialIngredient.SPEED_II_POTION
        ));
        assertFalse(service.matchesSpecialIngredient(
            new ItemStack(Material.POTION),
            SentryRecipeCatalog.SpecialIngredient.SPEED_II_POTION
        ));
    }
}

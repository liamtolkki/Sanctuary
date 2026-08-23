package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SanctuaryRecipeRegistrationTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void customFragmentIngredientUsesExactChoice() {
        var ingredient = SanctuaryRecipeCatalog.shapelessRecipes()
            .getFirst()
            .ingredients()
            .getFirst();

        RecipeChoice choice = SanctuaryRecipeService.registrationChoice(ingredient);

        assertInstanceOf(RecipeChoice.ExactChoice.class, choice);
        assertTrue(choice.test(
            ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT)
        ));
        assertFalse(choice.test(new ItemStack(Material.SMALL_AMETHYST_BUD)));
        assertFalse(choice.test(new ItemStack(Material.AMETHYST_SHARD)));
    }

    @Test
    void completedShardIngredientUsesExactChoice() {
        var beaconRecipe = SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(ExtendedItemIds.SANCTUARY_BEACON))
            .findFirst()
            .orElseThrow();

        RecipeChoice choice = SanctuaryRecipeService.registrationChoice(
            beaconRecipe.ingredients().get('S')
        );

        assertInstanceOf(RecipeChoice.ExactChoice.class, choice);
        assertTrue(choice.test(ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD)));
        assertFalse(choice.test(new ItemStack(Material.AMETHYST_SHARD)));
    }

    @Test
    void vanillaIngredientsRemainMaterialChoices() {
        var beaconRecipe = SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(ExtendedItemIds.SANCTUARY_BEACON))
            .findFirst()
            .orElseThrow();

        RecipeChoice choice = SanctuaryRecipeService.registrationChoice(
            beaconRecipe.ingredients().get('B')
        );

        assertInstanceOf(RecipeChoice.MaterialChoice.class, choice);
        assertTrue(choice.test(new ItemStack(Material.BEACON)));
        assertFalse(choice.test(ExtendedItems.create(ExtendedItemIds.SANCTUARY_BEACON)));
    }
}

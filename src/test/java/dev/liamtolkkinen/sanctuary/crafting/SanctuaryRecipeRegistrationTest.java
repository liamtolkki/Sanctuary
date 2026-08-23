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
    void customFragmentIngredientUsesBackingMaterialForRecipeBook() {
        var ingredient = SanctuaryRecipeCatalog.shapelessRecipes()
            .getFirst()
            .ingredients()
            .getFirst();

        RecipeChoice choice = SanctuaryRecipeService.registrationChoice(ingredient);

        assertInstanceOf(RecipeChoice.MaterialChoice.class, choice);
        assertTrue(choice.test(
            ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT)
        ));
        assertTrue(choice.test(new ItemStack(Material.SMALL_AMETHYST_BUD)));
        assertFalse(choice.test(new ItemStack(Material.AMETHYST_SHARD)));
    }

    @Test
    void completedShardIngredientUsesBackingMaterialForRecipeBook() {
        var beaconRecipe = SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(ExtendedItemIds.SANCTUARY_BEACON))
            .findFirst()
            .orElseThrow();

        RecipeChoice choice = SanctuaryRecipeService.registrationChoice(
            beaconRecipe.ingredients().get('S')
        );

        assertInstanceOf(RecipeChoice.MaterialChoice.class, choice);
        assertTrue(choice.test(ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD)));
        assertTrue(choice.test(new ItemStack(Material.AMETHYST_SHARD)));
    }

    @Test
    void recipeBookPlacementUsesExactCustomItemChoice() {
        var beaconRecipe = SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(ExtendedItemIds.SANCTUARY_BEACON))
            .findFirst()
            .orElseThrow();

        RecipeChoice choice = SanctuaryRecipeService.exactPlacementChoice(
            beaconRecipe.ingredients().get('S')
        );

        assertInstanceOf(RecipeChoice.ExactChoice.class, choice);
        assertTrue(choice.test(ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD)));
        assertFalse(choice.test(new ItemStack(Material.AMETHYST_SHARD)));
    }

    @Test
    void vanillaIngredientsRemainMaterialChoicesForRegistrationAndPlacement() {
        var beaconRecipe = SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(ExtendedItemIds.SANCTUARY_BEACON))
            .findFirst()
            .orElseThrow();

        var ingredient = beaconRecipe.ingredients().get('B');
        RecipeChoice registrationChoice = SanctuaryRecipeService.registrationChoice(ingredient);
        RecipeChoice placementChoice = SanctuaryRecipeService.exactPlacementChoice(ingredient);

        assertInstanceOf(RecipeChoice.MaterialChoice.class, registrationChoice);
        assertInstanceOf(RecipeChoice.MaterialChoice.class, placementChoice);
        assertTrue(registrationChoice.test(new ItemStack(Material.BEACON)));
        assertTrue(placementChoice.test(new ItemStack(Material.BEACON)));

        // MaterialChoice intentionally matches by vanilla material only. The
        // SanctuaryRecipeValidator is responsible for rejecting ExtendedItems
        // when a recipe slot explicitly requires a vanilla item.
        assertTrue(registrationChoice.test(
            ExtendedItems.create(ExtendedItemIds.SANCTUARY_BEACON)
        ));
    }
}

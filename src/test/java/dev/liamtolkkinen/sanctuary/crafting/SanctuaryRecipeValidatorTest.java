package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SanctuaryRecipeValidatorTest {
    private final SanctuaryRecipeValidator validator = new SanctuaryRecipeValidator();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void consecratedShardRequiresCraftingTable() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);
        ItemStack[] matrix = new ItemStack[4];
        for (int slot = 0; slot < matrix.length; slot++) {
            matrix[slot] = ExtendedItems.create(
                ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT
            );
        }

        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void consecratedShardUsesFullThreeByThreePatternInCraftingTable() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);
        ItemStack[] matrix = new ItemStack[9];
        for (int slot = 0; slot < matrix.length; slot++) {
            matrix[slot] = ExtendedItems.create(
                ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT
            );
        }
        assertTrue(validator.matches(recipe, matrix));

        ItemStack[] missingOne = matrix.clone();
        missingOne[8] = null;
        assertFalse(validator.matches(recipe, missingOne));
    }

    @Test
    void vanillaAmethystShardsCannotImpersonateConsecratedFragments() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);
        ItemStack[] valid = new ItemStack[9];
        for (int slot = 0; slot < valid.length; slot++) {
            valid[slot] = ExtendedItems.create(
                ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT
            );
        }
        assertTrue(validator.matches(recipe, valid));

        ItemStack[] vanilla = new ItemStack[9];
        for (int slot = 0; slot < vanilla.length; slot++) {
            vanilla[slot] = new ItemStack(Material.AMETHYST_SHARD);
        }
        assertFalse(validator.matches(recipe, vanilla));
    }

    @Test
    void vanillaAmethystShardCannotReplaceCustomShardInBeaconRecipe() {
        var recipe = shaped(ExtendedItemIds.SANCTUARY_BEACON);
        var matrix = matrixFor(recipe);
        assertTrue(validator.matches(recipe, matrix));

        matrix[0] = new ItemStack(Material.AMETHYST_SHARD);
        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void customSanctuaryBeaconCannotImpersonateVanillaBeaconIngredient() {
        var recipe = shaped(ExtendedItemIds.SANCTUARY_BEACON);
        var matrix = matrixFor(recipe);
        matrix[4] = ExtendedItems.create(ExtendedItemIds.SANCTUARY_BEACON);

        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void consecratedKeystoneRequiresCustomSanctuaryCoreIdentity() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_KEYSTONE);
        var matrix = matrixFor(recipe);
        assertTrue(validator.matches(recipe, matrix));

        matrix[4] = new ItemStack(Material.NETHER_STAR);
        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void sanctuaryConduitRequiresCustomCoreButVanillaConduit() {
        var recipe = shaped(ExtendedItemIds.SANCTUARY_CONDUIT);
        var matrix = matrixFor(recipe);
        assertTrue(validator.matches(recipe, matrix));

        matrix[4] = new ItemStack(Material.NETHER_STAR);
        assertFalse(validator.matches(recipe, matrix));

        matrix = matrixFor(recipe);
        matrix[7] = ExtendedItems.create(ExtendedItemIds.SANCTUARY_CONDUIT);
        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void divineAltarAcceptsVanillaLecternAndRejectsCustomAltarInItsPlace() {
        var recipe = shaped(ExtendedItemIds.DIVINE_ALTAR);
        var matrix = matrixFor(recipe);
        assertTrue(validator.matches(recipe, matrix));

        matrix[4] = ExtendedItems.create(ExtendedItemIds.DIVINE_ALTAR);
        assertFalse(validator.matches(recipe, matrix));
    }

    private static ItemStack[] matrixFor(
        SanctuaryRecipeCatalog.ShapedRecipeDefinition recipe
    ) {
        ItemStack[] matrix = new ItemStack[9];
        for (int slot = 0; slot < matrix.length; slot++) {
            char symbol = recipe.shape().get(slot / 3).charAt(slot % 3);
            if (symbol == ' ') {
                continue;
            }

            var ingredient = recipe.ingredients().get(symbol);
            matrix[slot] = ingredient.extendedItem() != null
                ? ExtendedItems.create(ingredient.extendedItem())
                : new ItemStack(ingredient.material());
        }
        return matrix;
    }

    private static SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped(
        ExtendedItemId result
    ) {
        return SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(result))
            .findFirst()
            .orElseThrow();
    }
}

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
    void consecratedShardFitsPlayerTwoByTwoCraftingGrid() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);
        ItemStack[] matrix = fragmentSquare(4);

        assertTrue(validator.matches(recipe, matrix));

        matrix[3] = null;
        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void consecratedShardAlsoWorksInCraftingTableAtAnyValidOffset() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);

        ItemStack[] topLeft = new ItemStack[9];
        topLeft[0] = fragment();
        topLeft[1] = fragment();
        topLeft[3] = fragment();
        topLeft[4] = fragment();
        assertTrue(validator.matches(recipe, topLeft));

        ItemStack[] bottomRight = new ItemStack[9];
        bottomRight[4] = fragment();
        bottomRight[5] = fragment();
        bottomRight[7] = fragment();
        bottomRight[8] = fragment();
        assertTrue(validator.matches(recipe, bottomRight));

        bottomRight[8] = null;
        assertFalse(validator.matches(recipe, bottomRight));
    }

    @Test
    void vanillaSmallAmethystBudsCannotImpersonateConsecratedFragments() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);
        assertTrue(validator.matches(recipe, fragmentSquare(4)));

        ItemStack[] vanilla = new ItemStack[] {
            new ItemStack(Material.SMALL_AMETHYST_BUD),
            new ItemStack(Material.SMALL_AMETHYST_BUD),
            new ItemStack(Material.SMALL_AMETHYST_BUD),
            new ItemStack(Material.SMALL_AMETHYST_BUD)
        };
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

        matrix[4] = new ItemStack(Material.END_CRYSTAL);
        assertFalse(validator.matches(recipe, matrix));
    }

    @Test
    void sanctuaryConduitRequiresCustomCoreButVanillaConduit() {
        var recipe = shaped(ExtendedItemIds.SANCTUARY_CONDUIT);
        var matrix = matrixFor(recipe);
        assertTrue(validator.matches(recipe, matrix));

        matrix[4] = new ItemStack(Material.END_CRYSTAL);
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

    private static ItemStack[] fragmentSquare(int size) {
        ItemStack[] matrix = new ItemStack[size];
        for (int slot = 0; slot < matrix.length; slot++) {
            matrix[slot] = fragment();
        }
        return matrix;
    }

    private static ItemStack fragment() {
        return ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT);
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

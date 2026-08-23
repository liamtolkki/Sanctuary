package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SentryRecipeCatalogTest {

    @Test
    void companionRecipesUseAtMostNineSlots() {
        assertEquals(12, SentryRecipeCatalog.companionRecipes().size());

        for (SentryRecipeCatalog.CompanionRecipe recipe
            : SentryRecipeCatalog.companionRecipes())
        {
            assertTrue(recipe.slotCount() >= 1, recipe.key());
            assertTrue(recipe.slotCount() <= 9, recipe.key());
        }
    }

    @Test
    void skeletonRecipeUsesThreeSkullsBoneBlocksAndArrows() {
        var recipe = companion(ExtendedItemIds.COMPANION_SKELETON);

        assertEquals(9, recipe.slotCount());
        assertEquals(3, materialCount(recipe, Material.SKELETON_SKULL));
        assertEquals(3, materialCount(recipe, Material.BONE_BLOCK));
        assertEquals(3, materialCount(recipe, Material.ARROW));
    }

    @Test
    void evokerRecipeUsesEightTotemsAndLevelFiveOminousBottle() {
        var recipe = companion(ExtendedItemIds.COMPANION_EVOKER);

        assertEquals(9, recipe.slotCount());
        assertEquals(8, materialCount(recipe, Material.TOTEM_OF_UNDYING));
        assertEquals(
            1,
            specialCount(
                recipe,
                SentryRecipeCatalog.SpecialIngredient.OMINOUS_BOTTLE_V
            )
        );
    }

    @Test
    void endermanAndWardenCompanionsAreNotCraftable() {
        Set<ExtendedItemId> craftable = SentryRecipeCatalog.companionRecipes()
            .stream()
            .map(SentryRecipeCatalog.CompanionRecipe::result)
            .collect(Collectors.toSet());

        assertFalse(craftable.contains(ExtendedItemIds.COMPANION_ENDERMAN));
        assertFalse(craftable.contains(ExtendedItemIds.COMPANION_WARDEN));
    }

    @Test
    void everyImplementedSentryHasCompanionToPostConversion() {
        Set<ExtendedItemId> implemented = SentryDefinition.ALL
            .stream()
            .map(SentryDefinition::itemId)
            .collect(Collectors.toSet());
        Set<ExtendedItemId> converted = SentryRecipeCatalog.sentryConversions()
            .stream()
            .map(SentryRecipeCatalog.SentryConversion::sentry)
            .collect(Collectors.toSet());

        assertEquals(implemented, converted);
        assertEquals(14, converted.size());
    }

    @Test
    void sentryConversionsUseCurrentExtendedItemsPostMaterials() {
        Map<ExtendedItemId, Material> materials =
            SentryRecipeCatalog.sentryConversions()
                .stream()
                .collect(
                    Collectors.toMap(
                        SentryRecipeCatalog.SentryConversion::sentry,
                        SentryRecipeCatalog.SentryConversion::postMaterial
                    )
                );

        assertEquals(
            Material.SMOOTH_STONE_SLAB,
            materials.get(ExtendedItemIds.SENTRY_IRON_GOLEM)
        );
        assertEquals(
            Material.PURPUR_SLAB,
            materials.get(ExtendedItemIds.SENTRY_ENDERMAN)
        );
        assertEquals(
            Material.SCULK_SENSOR,
            materials.get(ExtendedItemIds.SENTRY_WARDEN)
        );
        assertEquals(
            Material.WAXED_WEATHERED_CUT_COPPER_SLAB,
            materials.get(ExtendedItemIds.SENTRY_CREEPER)
        );
        assertEquals(
            Material.PRISMARINE_BRICK_SLAB,
            materials.get(ExtendedItemIds.SENTRY_ELDER_GUARDIAN)
        );
    }

    private static SentryRecipeCatalog.CompanionRecipe companion(
        ExtendedItemId id
    ) {
        return SentryRecipeCatalog.companionRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(id))
            .findFirst()
            .orElseThrow();
    }

    private static int materialCount(
        SentryRecipeCatalog.CompanionRecipe recipe,
        Material material
    ) {
        return recipe.ingredients()
            .stream()
            .filter(ingredient -> material.equals(ingredient.material()))
            .mapToInt(SentryRecipeCatalog.Ingredient::count)
            .sum();
    }

    private static int specialCount(
        SentryRecipeCatalog.CompanionRecipe recipe,
        SentryRecipeCatalog.SpecialIngredient special
    ) {
        return recipe.ingredients()
            .stream()
            .filter(ingredient -> special.equals(ingredient.special()))
            .mapToInt(SentryRecipeCatalog.Ingredient::count)
            .sum();
    }
}

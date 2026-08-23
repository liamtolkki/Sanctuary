package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SentryRecipeDiscoveryCatalogTest {

    @Test
    void companionRecipesRetainRepresentativeUnlockIngredients() {
        assertMaterial(ExtendedItemIds.COMPANION_IRON_GOLEM, Material.CARVED_PUMPKIN);
        assertSpecial(
            ExtendedItemIds.COMPANION_PILLAGER,
            SentryRecipeCatalog.SpecialIngredient.OMINOUS_BANNER
        );
        assertMaterial(ExtendedItemIds.COMPANION_SKELETON, Material.SKELETON_SKULL);
        assertSpecial(
            ExtendedItemIds.COMPANION_PIGLIN_BRUTE,
            SentryRecipeCatalog.SpecialIngredient.PIGLIN_BRUTE_TROPHY_HEAD
        );
        assertSpecial(
            ExtendedItemIds.COMPANION_EVOKER,
            SentryRecipeCatalog.SpecialIngredient.OMINOUS_BOTTLE_V
        );
        assertSpecial(
            ExtendedItemIds.COMPANION_BABY_ZOMBIE,
            SentryRecipeCatalog.SpecialIngredient.ZOMBIE_TROPHY_HEAD
        );
        assertMaterial(ExtendedItemIds.COMPANION_BLAZE, Material.BLAZE_ROD);
        assertSpecial(
            ExtendedItemIds.COMPANION_CREEPER,
            SentryRecipeCatalog.SpecialIngredient.CREEPER_TROPHY_HEAD
        );
        assertMaterial(
            ExtendedItemIds.COMPANION_WITHER,
            Material.WITHER_SKELETON_SKULL
        );
        assertMaterial(ExtendedItemIds.COMPANION_DROWNED, Material.TRIDENT);
        assertMaterial(ExtendedItemIds.COMPANION_GUARDIAN, Material.HEART_OF_THE_SEA);
        assertMaterial(ExtendedItemIds.COMPANION_ELDER_GUARDIAN, Material.CONDUIT);
    }

    private static void assertMaterial(ExtendedItemId companion, Material material) {
        SentryRecipeCatalog.UnlockIngredient unlock = companion(companion).unlockIngredient();
        assertEquals(material, unlock.material());
        assertEquals(null, unlock.special());
    }

    private static void assertSpecial(
        ExtendedItemId companion,
        SentryRecipeCatalog.SpecialIngredient special
    ) {
        SentryRecipeCatalog.UnlockIngredient unlock = companion(companion).unlockIngredient();
        assertEquals(null, unlock.material());
        assertEquals(special, unlock.special());
    }

    private static SentryRecipeCatalog.CompanionRecipe companion(ExtendedItemId id) {
        return SentryRecipeCatalog.companionRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(id))
            .findFirst()
            .orElseThrow();
    }
}

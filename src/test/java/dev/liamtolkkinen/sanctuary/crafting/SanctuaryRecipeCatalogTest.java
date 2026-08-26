package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SanctuaryRecipeCatalogTest {
    @Test
    void catalogContainsOnlyCurrentCraftingRecipes() {
        assertEquals(10, SanctuaryRecipeCatalog.allRecipes().size());
        assertEquals(0, SanctuaryRecipeCatalog.shapelessRecipes().size());
        assertEquals(10, SanctuaryRecipeCatalog.shapedRecipes().size());

        Set<?> results = SanctuaryRecipeCatalog.allRecipes()
            .stream()
            .map(SanctuaryRecipeCatalog.RecipeDefinition::result)
            .collect(Collectors.toSet());

        assertFalse(results.contains(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT));
        assertEquals(10, results.size());
    }

    @Test
    void consecratedShardUsesFourCustomFragmentsInTwoByTwoPattern() {
        var recipe = shaped(ExtendedItemIds.CONSECRATED_SHARD);
        assertEquals(List.of("FF ", "FF ", "   "), recipe.shape());
        assertEquals(List.of("FF", "FF"), SanctuaryRecipeCatalog.compactShape(recipe));
        assertEquals(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT,
            recipe.ingredients().get('F').extendedItem());
    }

    @Test
    void sanctuaryBeaconPreservesBeaconLikeShapeAndQuartzBase() {
        var recipe = shaped(ExtendedItemIds.SANCTUARY_BEACON);
        assertEquals(List.of("SSS", "SBS", "QQQ"), recipe.shape());
        assertEquals(ExtendedItemIds.CONSECRATED_SHARD,
            recipe.ingredients().get('S').extendedItem());
        assertEquals(Material.BEACON, recipe.ingredients().get('B').material());
        assertEquals(Material.QUARTZ_BLOCK, recipe.ingredients().get('Q').material());
    }

    @Test
    void currentArtifactShapesMatchDesign() {
        assertShape(ExtendedItemIds.SANCTUARY_CORE, "SSS", "SES", "SSS");
        assertShape(ExtendedItemIds.TERRITORY_KEYSTONE, "SPS", "PLP", "SPS");
        assertShape(ExtendedItemIds.WATCHERS_EYE, "SPS", "PEP", "SPS");
        assertShape(ExtendedItemIds.ATTUNEMENT_RELIC, "SCS", "CEC", "SCS");
        assertShape(ExtendedItemIds.CONSECRATED_KEYSTONE, "SCS", "CKC", "SCS");
        assertShape(ExtendedItemIds.SANCTUARY_CONDUIT, "SSS", "SCS", "SDS");
        assertShape(ExtendedItemIds.DIVINE_ALTAR, "S S", " L ", "S S");
        assertShape(ExtendedItemIds.DIVINE_RELIC, "TCT", "CNC", "TCT");
    }

    @Test
    void sanctuaryCoreUsesVanillaEndCrystal() {
        var recipe = shaped(ExtendedItemIds.SANCTUARY_CORE);
        assertEquals(Material.END_CRYSTAL, recipe.ingredients().get('E').material());
    }

    @Test
    void attunementRelicUsesSentinelSealRecipeIdentity() {
        var recipe = shaped(ExtendedItemIds.ATTUNEMENT_RELIC);
        assertEquals(Material.SCULK_CATALYST, recipe.ingredients().get('C').material());
        assertEquals(Material.ECHO_SHARD, recipe.ingredients().get('E').material());
    }

    @Test
    void customCoreIsRequiredWhereAgreed() {
        assertEquals(
            ExtendedItemIds.SANCTUARY_CORE,
            shaped(ExtendedItemIds.CONSECRATED_KEYSTONE).ingredients().get('K').extendedItem()
        );
        assertEquals(
            ExtendedItemIds.SANCTUARY_CORE,
            shaped(ExtendedItemIds.SANCTUARY_CONDUIT).ingredients().get('C').extendedItem()
        );
        assertEquals(
            ExtendedItemIds.SANCTUARY_CORE,
            shaped(ExtendedItemIds.DIVINE_RELIC).ingredients().get('C').extendedItem()
        );
    }

    @Test
    void divineRelicRecipeIsPainfullyRenewable() {
        var recipe = shaped(ExtendedItemIds.DIVINE_RELIC);
        assertEquals(Material.TOTEM_OF_UNDYING, recipe.ingredients().get('T').material());
        assertEquals(Material.NETHER_STAR, recipe.ingredients().get('N').material());
    }

    private static void assertShape(ExtendedItemId id, String top, String middle, String bottom) {
        assertEquals(List.of(top, middle, bottom), shaped(id).shape());
    }

    private static SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped(ExtendedItemId result) {
        return SanctuaryRecipeCatalog.shapedRecipes()
            .stream()
            .filter(recipe -> recipe.result().equals(result))
            .findFirst()
            .orElseThrow();
    }
}

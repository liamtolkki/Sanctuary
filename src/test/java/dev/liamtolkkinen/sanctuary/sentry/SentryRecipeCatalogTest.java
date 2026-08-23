package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SentryRecipeCatalogTest {

    @Test
    void everyCraftableCompanionUsesAFullThreeByThreeShape() {
        assertEquals(12, SentryRecipeCatalog.companionRecipes().size());

        for (SentryRecipeCatalog.CompanionRecipe recipe
            : SentryRecipeCatalog.companionRecipes())
        {
            assertEquals(3, recipe.shape().size(), recipe.key());
            assertEquals("", recipe.shape().stream()
                .filter(row -> row.length() != 3)
                .findFirst()
                .orElse(""), recipe.key());
            assertEquals(9, recipe.slotCount(), recipe.key());
        }
    }

    @Test
    void ironGolemUsesCornerShardsPumpkinAndIronBlocks() {
        var recipe = companion(ExtendedItemIds.COMPANION_IRON_GOLEM);
        assertShape(recipe, "SPS", "III", "SIS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'P', Material.CARVED_PUMPKIN);
        assertMaterial(recipe, 'I', Material.IRON_BLOCK);
    }

    @Test
    void pillagerUsesShardRowsAndOminousCenterRow() {
        var recipe = companion(ExtendedItemIds.COMPANION_PILLAGER);
        assertShape(recipe, "SSS", "BCO", "SSS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertSpecial(recipe, 'B', SentryRecipeCatalog.SpecialIngredient.OMINOUS_BANNER);
        assertMaterial(recipe, 'C', Material.CROSSBOW);
        assertSpecial(recipe, 'O', SentryRecipeCatalog.SpecialIngredient.OMINOUS_BOTTLE_V);
    }

    @Test
    void skeletonUsesShardRowsAndBowSkullArrow() {
        var recipe = companion(ExtendedItemIds.COMPANION_SKELETON);
        assertShape(recipe, "SSS", "BKA", "SSS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'B', Material.BOW);
        assertMaterial(recipe, 'K', Material.SKELETON_SKULL);
        assertMaterial(recipe, 'A', Material.ARROW);
    }

    @Test
    void piglinBruteUsesShardsGoldHeadAxeAndScrap() {
        var recipe = companion(ExtendedItemIds.COMPANION_PIGLIN_BRUTE);
        assertShape(recipe, "SSS", "GHA", "NNN");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'G', Material.GOLD_BLOCK);
        assertSpecial(recipe, 'H', SentryRecipeCatalog.SpecialIngredient.PIGLIN_BRUTE_TROPHY_HEAD);
        assertMaterial(recipe, 'A', Material.GOLDEN_AXE);
        assertMaterial(recipe, 'N', Material.NETHERITE_SCRAP);
    }

    @Test
    void evokerUsesCornerShardsTotemsAndOminousBottleFive() {
        var recipe = companion(ExtendedItemIds.COMPANION_EVOKER);
        assertShape(recipe, "STS", "TBT", "STS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'T', Material.TOTEM_OF_UNDYING);
        assertSpecial(recipe, 'B', SentryRecipeCatalog.SpecialIngredient.OMINOUS_BOTTLE_V);
    }

    @Test
    void babyZombieUsesSpeedTwoAndNetheriteCenterRow() {
        var recipe = companion(ExtendedItemIds.COMPANION_BABY_ZOMBIE);
        assertShape(recipe, "SPS", "HZW", "SSS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertSpecial(recipe, 'P', SentryRecipeCatalog.SpecialIngredient.SPEED_II_POTION);
        assertMaterial(recipe, 'H', Material.NETHERITE_HELMET);
        assertSpecial(recipe, 'Z', SentryRecipeCatalog.SpecialIngredient.ZOMBIE_TROPHY_HEAD);
        assertMaterial(recipe, 'W', Material.NETHERITE_SWORD);
    }

    @Test
    void blazeUsesEightBlazeRodsAroundOneShard() {
        var recipe = companion(ExtendedItemIds.COMPANION_BLAZE);
        assertShape(recipe, "RRR", "RSR", "RRR");
        assertMaterial(recipe, 'R', Material.BLAZE_ROD);
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
    }

    @Test
    void creeperUsesShardRowsAndTntHeadTnt() {
        var recipe = companion(ExtendedItemIds.COMPANION_CREEPER);
        assertShape(recipe, "SSS", "THT", "SSS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'T', Material.TNT);
        assertSpecial(recipe, 'H', SentryRecipeCatalog.SpecialIngredient.CREEPER_TROPHY_HEAD);
    }

    @Test
    void witherUsesSkullsCoresDivineRelicAndShards() {
        var recipe = companion(ExtendedItemIds.COMPANION_WITHER);
        assertShape(recipe, "KKK", "CDC", "SSS");
        assertMaterial(recipe, 'K', Material.WITHER_SKELETON_SKULL);
        assertExtended(recipe, 'C', ExtendedItemIds.SANCTUARY_CORE);
        assertExtended(recipe, 'D', ExtendedItemIds.DIVINE_RELIC);
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
    }

    @Test
    void drownedUsesShardRowsAndNetheriteHeadTrident() {
        var recipe = companion(ExtendedItemIds.COMPANION_DROWNED);
        assertShape(recipe, "SSS", "HZT", "SSS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'H', Material.NETHERITE_HELMET);
        assertSpecial(recipe, 'Z', SentryRecipeCatalog.SpecialIngredient.ZOMBIE_TROPHY_HEAD);
        assertMaterial(recipe, 'T', Material.TRIDENT);
    }

    @Test
    void guardianUsesCornerShardsEdgeSpongesAndHeart() {
        var recipe = companion(ExtendedItemIds.COMPANION_GUARDIAN);
        assertShape(recipe, "SPS", "PHP", "SPS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'P', Material.SPONGE);
        assertMaterial(recipe, 'H', Material.HEART_OF_THE_SEA);
    }

    @Test
    void elderGuardianUsesShardRowsAndConduitHeartConduit() {
        var recipe = companion(ExtendedItemIds.COMPANION_ELDER_GUARDIAN);
        assertShape(recipe, "SSS", "CHC", "SSS");
        assertExtended(recipe, 'S', ExtendedItemIds.CONSECRATED_SHARD);
        assertMaterial(recipe, 'C', Material.CONDUIT);
        assertMaterial(recipe, 'H', Material.HEART_OF_THE_SEA);
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

    private static void assertShape(
        SentryRecipeCatalog.CompanionRecipe recipe,
        String top,
        String middle,
        String bottom
    ) {
        assertEquals(java.util.List.of(top, middle, bottom), recipe.shape());
    }

    private static void assertMaterial(
        SentryRecipeCatalog.CompanionRecipe recipe,
        char symbol,
        Material material
    ) {
        var ingredient = recipe.ingredients().get(symbol);
        assertEquals(material, ingredient.material());
        assertEquals(null, ingredient.extendedItem());
        assertEquals(null, ingredient.special());
    }

    private static void assertExtended(
        SentryRecipeCatalog.CompanionRecipe recipe,
        char symbol,
        ExtendedItemId itemId
    ) {
        var ingredient = recipe.ingredients().get(symbol);
        assertEquals(null, ingredient.material());
        assertEquals(itemId, ingredient.extendedItem());
        assertEquals(null, ingredient.special());
    }

    private static void assertSpecial(
        SentryRecipeCatalog.CompanionRecipe recipe,
        char symbol,
        SentryRecipeCatalog.SpecialIngredient special
    ) {
        var ingredient = recipe.ingredients().get(symbol);
        assertEquals(null, ingredient.material());
        assertEquals(null, ingredient.extendedItem());
        assertEquals(special, ingredient.special());
    }
}

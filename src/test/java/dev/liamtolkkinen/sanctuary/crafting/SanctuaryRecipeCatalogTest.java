package dev.liamtolkkinen.sanctuary.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SanctuaryRecipeCatalogTest {
    @Test void catalogContainsOnlyTheFourteenAgreedCraftingRecipes(){assertEquals(14,SanctuaryRecipeCatalog.allRecipes().size());assertEquals(1,SanctuaryRecipeCatalog.shapelessRecipes().size());assertEquals(13,SanctuaryRecipeCatalog.shapedRecipes().size());Set<?> results=SanctuaryRecipeCatalog.allRecipes().stream().map(SanctuaryRecipeCatalog.RecipeDefinition::result).collect(Collectors.toSet());assertFalse(results.contains(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT));assertFalse(results.contains(ExtendedItemIds.DIVINE_RELIC));}
    @Test void consecratedShardUsesFourCustomFragmentsShapeless(){var recipe=SanctuaryRecipeCatalog.shapelessRecipes().getFirst();assertEquals(ExtendedItemIds.CONSECRATED_SHARD,recipe.result());assertEquals(4,recipe.ingredients().size());assertTrue(recipe.ingredients().stream().allMatch(i->ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT.equals(i.extendedItem())));}
    @Test void sanctuaryBeaconPreservesBeaconLikeShapeAndQuartzBase(){var r=shaped(ExtendedItemIds.SANCTUARY_BEACON);assertEquals(List.of("SSS","SBS","QQQ"),r.shape());assertEquals(ExtendedItemIds.CONSECRATED_SHARD,r.ingredients().get('S').extendedItem());assertEquals(Material.BEACON,r.ingredients().get('B').material());assertEquals(Material.QUARTZ_BLOCK,r.ingredients().get('Q').material());}
    @Test void exactShapesMatchAgreedDesign(){
        assertShape(ExtendedItemIds.SANCTUARY_CORE,"SSS","SNS","SSS");assertShape(ExtendedItemIds.TERRITORY_KEYSTONE,"SPS","PLP","SPS");assertShape(ExtendedItemIds.WATCHERS_EYE,"SPS","PEP","SPS");assertShape(ExtendedItemIds.WARD_STONE,"SIS","IOI","SIS");assertShape(ExtendedItemIds.BLAST_WARD,"SGS","GCG","SGS");assertShape(ExtendedItemIds.PURIFICATION_RELIC,"SAS","ATA","SAS");assertShape(ExtendedItemIds.SEAL_OF_KEEPING,"SHS","HEH","SHS");assertShape(ExtendedItemIds.GUARDIAN_TOKEN,"SNS","NHN","SNS");assertShape(ExtendedItemIds.SENTINEL_SEAL,"SCS","CEC","SCS");assertShape(ExtendedItemIds.CONSECRATED_KEYSTONE,"SCS","CKC","SCS");assertShape(ExtendedItemIds.SANCTUARY_CONDUIT,"SSS","SCS","SDS");assertShape(ExtendedItemIds.DIVINE_ALTAR,"S S"," L ","S S");
    }
    @Test void customCoreIsRequiredWhereAgreed(){assertEquals(ExtendedItemIds.SANCTUARY_CORE,shaped(ExtendedItemIds.CONSECRATED_KEYSTONE).ingredients().get('K').extendedItem());assertEquals(ExtendedItemIds.SANCTUARY_CORE,shaped(ExtendedItemIds.SANCTUARY_CONDUIT).ingredients().get('C').extendedItem());}
    @Test void artifactRecipesKeepAgreedVanillaIngredients(){assertEquals(Material.ENDER_PEARL,shaped(ExtendedItemIds.TERRITORY_KEYSTONE).ingredients().get('P').material());assertEquals(Material.LODESTONE,shaped(ExtendedItemIds.TERRITORY_KEYSTONE).ingredients().get('L').material());assertEquals(Material.ENDER_EYE,shaped(ExtendedItemIds.WATCHERS_EYE).ingredients().get('E').material());assertEquals(Material.IRON_BLOCK,shaped(ExtendedItemIds.WARD_STONE).ingredients().get('I').material());assertEquals(Material.GUNPOWDER,shaped(ExtendedItemIds.BLAST_WARD).ingredients().get('G').material());assertEquals(Material.GOLDEN_APPLE,shaped(ExtendedItemIds.PURIFICATION_RELIC).ingredients().get('A').material());assertEquals(Material.SHULKER_SHELL,shaped(ExtendedItemIds.SEAL_OF_KEEPING).ingredients().get('H').material());assertEquals(Material.NAUTILUS_SHELL,shaped(ExtendedItemIds.GUARDIAN_TOKEN).ingredients().get('N').material());assertEquals(Material.SCULK_CATALYST,shaped(ExtendedItemIds.SENTINEL_SEAL).ingredients().get('C').material());assertEquals(Material.LECTERN,shaped(ExtendedItemIds.DIVINE_ALTAR).ingredients().get('L').material());}
    private static void assertShape(ExtendedItemId id,String a,String b,String c){assertEquals(List.of(a,b,c),shaped(id).shape());}
    private static SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped(ExtendedItemId result){return SanctuaryRecipeCatalog.shapedRecipes().stream().filter(r->r.result().equals(result)).findFirst().orElseThrow();}
}

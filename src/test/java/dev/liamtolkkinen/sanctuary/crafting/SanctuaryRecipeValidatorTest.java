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
    private final SanctuaryRecipeValidator validator=new SanctuaryRecipeValidator();
    @BeforeEach void setUp(){MockBukkit.mock();} @AfterEach void tearDown(){MockBukkit.unmock();}
    @Test void vanillaAmethystShardsCannotImpersonateConsecratedFragments(){var recipe=SanctuaryRecipeCatalog.shapelessRecipes().getFirst();ItemStack[] valid=new ItemStack[9];for(int i=0;i<4;i++)valid[i]=ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT);assertTrue(validator.matches(recipe,valid));ItemStack[] vanilla=new ItemStack[9];for(int i=0;i<4;i++)vanilla[i]=new ItemStack(Material.AMETHYST_SHARD);assertFalse(validator.matches(recipe,vanilla));}
    @Test void vanillaAmethystShardCannotReplaceCustomShardInBeaconRecipe(){var r=shaped(ExtendedItemIds.SANCTUARY_BEACON);var m=matrixFor(r);assertTrue(validator.matches(r,m));m[0]=new ItemStack(Material.AMETHYST_SHARD);assertFalse(validator.matches(r,m));}
    @Test void customSanctuaryBeaconCannotImpersonateVanillaBeaconIngredient(){var r=shaped(ExtendedItemIds.SANCTUARY_BEACON);var m=matrixFor(r);m[4]=ExtendedItems.create(ExtendedItemIds.SANCTUARY_BEACON);assertFalse(validator.matches(r,m));}
    @Test void consecratedKeystoneRequiresCustomSanctuaryCoreIdentity(){var r=shaped(ExtendedItemIds.CONSECRATED_KEYSTONE);var m=matrixFor(r);assertTrue(validator.matches(r,m));m[4]=new ItemStack(Material.NETHER_STAR);assertFalse(validator.matches(r,m));}
    @Test void sanctuaryConduitRequiresCustomCoreButVanillaConduit(){var r=shaped(ExtendedItemIds.SANCTUARY_CONDUIT);var m=matrixFor(r);assertTrue(validator.matches(r,m));m[4]=new ItemStack(Material.NETHER_STAR);assertFalse(validator.matches(r,m));m=matrixFor(r);m[7]=ExtendedItems.create(ExtendedItemIds.SANCTUARY_CONDUIT);assertFalse(validator.matches(r,m));}
    @Test void divineAltarAcceptsVanillaLecternAndRejectsCustomAltarInItsPlace(){var r=shaped(ExtendedItemIds.DIVINE_ALTAR);var m=matrixFor(r);assertTrue(validator.matches(r,m));m[4]=ExtendedItems.create(ExtendedItemIds.DIVINE_ALTAR);assertFalse(validator.matches(r,m));}
    private static ItemStack[] matrixFor(SanctuaryRecipeCatalog.ShapedRecipeDefinition r){ItemStack[] m=new ItemStack[9];for(int slot=0;slot<9;slot++){char s=r.shape().get(slot/3).charAt(slot%3);if(s==' ')continue;var i=r.ingredients().get(s);m[slot]=i.extendedItem()!=null?ExtendedItems.create(i.extendedItem()):new ItemStack(i.material());}return m;}
    private static SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped(ExtendedItemId result){return SanctuaryRecipeCatalog.shapedRecipes().stream().filter(r->r.result().equals(result)).findFirst().orElseThrow();}
}

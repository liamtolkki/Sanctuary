package dev.liamtolkkinen.sanctuary.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SanctuaryAdvancementCatalogTest {
    @Test void progressionTreeHasUniqueKeysAndParentsDeclaredBeforeChildren(){Set<String> seen=new HashSet<>();for(var d:SanctuaryAdvancementCatalog.definitions()){assertTrue(seen.add(d.key()),d.key());if(d.parentKey()!=null)assertTrue(seen.contains(d.parentKey()),d.key());}assertEquals(12,seen.size());}
    @Test void fragmentIsRootAndQuestFormsOneOrderedBranch(){var fragment=SanctuaryAdvancementCatalog.find(SanctuaryAdvancementCatalog.FIRST_FRAGMENT).orElseThrow();assertEquals(null,fragment.parentKey());assertEquals(Material.SMALL_AMETHYST_BUD,fragment.icon());assertEquals(SanctuaryAdvancementCatalog.DIVINE_ALTAR,parentOf(SanctuaryAdvancementCatalog.FIRST_OFFERING));assertEquals(SanctuaryAdvancementCatalog.FIRST_OFFERING,parentOf(SanctuaryAdvancementCatalog.HALF_OFFERINGS));assertEquals(SanctuaryAdvancementCatalog.HALF_OFFERINGS,parentOf(SanctuaryAdvancementCatalog.ALL_OFFERINGS));assertEquals(SanctuaryAdvancementCatalog.ALL_OFFERINGS,parentOf(SanctuaryAdvancementCatalog.DIVINE_RELIC));}
    @Test void madeWholeDescribesNineFragmentRecipe(){var shard=SanctuaryAdvancementCatalog.find(SanctuaryAdvancementCatalog.FIRST_SHARD).orElseThrow();assertEquals("Made Whole",shard.title());assertEquals("Combine nine fragments into a Consecrated Shard.",shard.description());}
    @Test void masterArtificerTracksAllTenMajorProgressionArtifacts(){var c=SanctuaryAdvancementCatalog.masterArtifactCriteria();assertEquals(10,c.size());assertNotNull(c.get(ExtendedItemIds.WATCHERS_EYE));assertNotNull(c.get(ExtendedItemIds.WARD_STONE));assertNotNull(c.get(ExtendedItemIds.BLAST_WARD));assertNotNull(c.get(ExtendedItemIds.GUARDIAN_TOKEN));assertNotNull(c.get(ExtendedItemIds.PURIFICATION_RELIC));assertNotNull(c.get(ExtendedItemIds.TERRITORY_KEYSTONE));assertNotNull(c.get(ExtendedItemIds.SEAL_OF_KEEPING));assertNotNull(c.get(ExtendedItemIds.SENTINEL_SEAL));assertNotNull(c.get(ExtendedItemIds.SANCTUARY_CORE));assertNotNull(c.get(ExtendedItemIds.CONSECRATED_KEYSTONE));}
    @Test void advancementTreeIncludesBeaconConduitAndSentryMilestones(){assertEquals(SanctuaryAdvancementCatalog.FIRST_SHARD,parentOf(SanctuaryAdvancementCatalog.SANCTUARY_BEACON));assertEquals(SanctuaryAdvancementCatalog.SANCTUARY_BEACON,parentOf(SanctuaryAdvancementCatalog.SANCTUARY_CONDUIT));assertEquals(SanctuaryAdvancementCatalog.SANCTUARY_BEACON,parentOf(SanctuaryAdvancementCatalog.FIRST_SENTRY));}
    private static String parentOf(String key){return SanctuaryAdvancementCatalog.find(key).orElseThrow().parentKey();}
}

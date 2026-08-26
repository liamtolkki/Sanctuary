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
    @Test
    void progressionTreeHasUniqueKeysAndParentsDeclaredBeforeChildren() {
        Set<String> seen = new HashSet<>();
        for (var definition : SanctuaryAdvancementCatalog.definitions()) {
            assertTrue(seen.add(definition.key()), definition.key());
            if (definition.parentKey() != null) {
                assertTrue(seen.contains(definition.parentKey()), definition.key());
            }
        }
        assertEquals(12, seen.size());
    }

    @Test
    void fragmentIsRootAndOfferingQuestFormsOneOrderedBranch() {
        var fragment = SanctuaryAdvancementCatalog.find(
            SanctuaryAdvancementCatalog.FIRST_FRAGMENT
        ).orElseThrow();
        assertEquals(null, fragment.parentKey());
        assertEquals(Material.SMALL_AMETHYST_BUD, fragment.icon());
        assertEquals(
            SanctuaryAdvancementCatalog.DIVINE_ALTAR,
            parentOf(SanctuaryAdvancementCatalog.FIRST_OFFERING)
        );
        assertEquals(
            SanctuaryAdvancementCatalog.FIRST_OFFERING,
            parentOf(SanctuaryAdvancementCatalog.HALF_OFFERINGS)
        );
        assertEquals(
            SanctuaryAdvancementCatalog.HALF_OFFERINGS,
            parentOf(SanctuaryAdvancementCatalog.ALL_OFFERINGS)
        );
        assertEquals(
            SanctuaryAdvancementCatalog.ALL_OFFERINGS,
            parentOf(SanctuaryAdvancementCatalog.DIVINE_RELIC)
        );
    }

    @Test
    void madeWholeDescribesFourFragmentRecipe() {
        var shard = SanctuaryAdvancementCatalog.find(
            SanctuaryAdvancementCatalog.FIRST_SHARD
        ).orElseThrow();
        assertEquals("Made Whole", shard.title());
        assertEquals(
            "Combine four fragments into a Consecrated Shard.",
            shard.description()
        );
    }

    @Test
    void masterArtificerTracksFiveMajorProgressionArtifacts() {
        var criteria = SanctuaryAdvancementCatalog.masterArtifactCriteria();
        assertEquals(5, criteria.size());
        assertNotNull(criteria.get(ExtendedItemIds.SANCTUARY_CORE));
        assertNotNull(criteria.get(ExtendedItemIds.TERRITORY_KEYSTONE));
        assertNotNull(criteria.get(ExtendedItemIds.WATCHERS_EYE));
        assertNotNull(criteria.get(ExtendedItemIds.ATTUNEMENT_RELIC));
        assertNotNull(criteria.get(ExtendedItemIds.CONSECRATED_KEYSTONE));
    }

    @Test
    void divineRelicAdvancementDescribesPermanentRecipeUnlock() {
        var relic = SanctuaryAdvancementCatalog.find(
            SanctuaryAdvancementCatalog.DIVINE_RELIC
        ).orElseThrow();
        assertEquals(Material.TOTEM_OF_UNDYING, relic.icon());
        assertEquals(
            "Receive your first Divine Relic and unlock its altar recipe.",
            relic.description()
        );
    }

    @Test
    void advancementTreeIncludesBeaconConduitAndSentryMilestones() {
        assertEquals(
            SanctuaryAdvancementCatalog.FIRST_SHARD,
            parentOf(SanctuaryAdvancementCatalog.SANCTUARY_BEACON)
        );
        assertEquals(
            SanctuaryAdvancementCatalog.SANCTUARY_BEACON,
            parentOf(SanctuaryAdvancementCatalog.SANCTUARY_CONDUIT)
        );
        assertEquals(
            SanctuaryAdvancementCatalog.SANCTUARY_BEACON,
            parentOf(SanctuaryAdvancementCatalog.FIRST_SENTRY)
        );
    }

    private static String parentOf(String key) {
        return SanctuaryAdvancementCatalog.find(key).orElseThrow().parentKey();
    }
}

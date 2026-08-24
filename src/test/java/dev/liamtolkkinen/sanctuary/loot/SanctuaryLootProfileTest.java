package dev.liamtolkkinen.sanctuary.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SanctuaryLootProfileTest {

    @Test
    void onlySelectedExplorationLootTablesReceiveSanctuaryMaterials() {
        assertEquals(6, SanctuaryLootProfile.all().size());
    }

    @Test
    void topTierStructuresHaveHighestFragmentRates() {
        assertRate(SanctuaryLootProfile.BASTION_TREASURE, 0.85, 2, 4, 0.25);
        assertRate(SanctuaryLootProfile.ANCIENT_CITY, 0.45, 1, 3, 0.20);
        assertRate(SanctuaryLootProfile.END_CITY, 0.35, 1, 3, 0.15);
    }

    @Test
    void buriedTreasureAlwaysAwardsTwoToFourFragmentsWhenFragmentRollHits() {
        assertRate(SanctuaryLootProfile.BURIED_TREASURE, 0.65, 2, 4, 0.10);
    }

    @Test
    void strongholdLibraryIsAboveMineshaftButBelowPremiumStructures() {
        assertRate(SanctuaryLootProfile.MINESHAFT, 0.10, 1, 1, 0.02);
        assertRate(SanctuaryLootProfile.STRONGHOLD_LIBRARY, 0.25, 1, 2, 0.07);
    }

    @Test
    void allProfilesHaveValidDistinctIdsAndLootTables() {
        assertEquals(
            SanctuaryLootProfile.all().size(),
            SanctuaryLootProfile.all().stream()
                .map(SanctuaryLootProfile::id)
                .distinct()
                .count()
        );

        for (SanctuaryLootProfile profile : SanctuaryLootProfile.all()) {
            assertTrue(profile.fragmentChance() >= 0.0 && profile.fragmentChance() <= 1.0);
            assertTrue(profile.shardChance() >= 0.0 && profile.shardChance() <= 1.0);
            assertTrue(profile.minimumFragments() >= 1);
            assertTrue(profile.maximumFragments() >= profile.minimumFragments());
            assertEquals(profile.lootTableType().getKey(), profile.lootTableKey());
        }
    }

    @Test
    void parserUsesStableDebugProfileIds() {
        assertEquals(
            SanctuaryLootProfile.BASTION_TREASURE,
            SanctuaryLootProfile.parse("bastion_treasure").orElseThrow()
        );
        assertEquals(
            SanctuaryLootProfile.END_CITY,
            SanctuaryLootProfile.parse("END_CITY").orElseThrow()
        );
    }

    private static void assertRate(
        SanctuaryLootProfile profile,
        double fragmentChance,
        int minimumFragments,
        int maximumFragments,
        double shardChance
    ) {
        assertEquals(fragmentChance, profile.fragmentChance());
        assertEquals(minimumFragments, profile.minimumFragments());
        assertEquals(maximumFragments, profile.maximumFragments());
        assertEquals(shardChance, profile.shardChance());
    }
}

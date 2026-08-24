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
        assertRate(SanctuaryLootProfile.BASTION_TREASURE, 1.00, 3, 6, 0.40);
        assertRate(SanctuaryLootProfile.ANCIENT_CITY, 0.80, 2, 5, 0.30);
        assertRate(SanctuaryLootProfile.END_CITY, 0.65, 2, 4, 0.20);
    }

    @Test
    void buriedTreasureAlwaysAwardsTwoToFourFragments() {
        assertRate(SanctuaryLootProfile.BURIED_TREASURE, 1.00, 2, 4, 0.15);
    }

    @Test
    void strongholdLibraryIsAboveMineshaftButBelowPremiumStructures() {
        assertRate(SanctuaryLootProfile.MINESHAFT, 0.20, 1, 2, 0.03);
        assertRate(SanctuaryLootProfile.STRONGHOLD_LIBRARY, 0.50, 2, 3, 0.10);
    }

    @Test
    void onlyEndCityCanAwardEndermanCompanionEgg() {
        for (SanctuaryLootProfile profile : SanctuaryLootProfile.all()) {
            double expected = profile == SanctuaryLootProfile.END_CITY ? 0.08 : 0.0;
            assertEquals(expected, profile.endermanCompanionChance());
        }
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
            assertTrue(
                profile.endermanCompanionChance() >= 0.0
                    && profile.endermanCompanionChance() <= 1.0
            );
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

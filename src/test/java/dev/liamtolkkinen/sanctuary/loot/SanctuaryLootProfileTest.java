package dev.liamtolkkinen.sanctuary.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.loot.LootTables;
import org.junit.jupiter.api.Test;

class SanctuaryLootProfileTest {

    @Test
    void bastionRatesMatchNetheriteInspiredBalance() {
        assertRate(SanctuaryLootProfile.BASTION_OTHER, 0.135, 0.045);
        assertRate(SanctuaryLootProfile.BASTION_BRIDGE, 0.135, 0.045);
        assertRate(SanctuaryLootProfile.BASTION_HOGLIN_STABLE, 0.12, 0.09);
        assertRate(SanctuaryLootProfile.BASTION_TREASURE, 0.27, 0.217);
    }

    @Test
    void premiumAncientAndEndCityLootUsesHigherRates() {
        assertRate(SanctuaryLootProfile.ANCIENT_CITY, 0.30, 0.15);
        assertRate(SanctuaryLootProfile.END_CITY, 0.30, 0.15);
    }

    @Test
    void allProfilesHaveValidDistinctIdsAndLootTables() {
        assertEquals(20, SanctuaryLootProfile.all().size());
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
        double shardChance
    ) {
        assertEquals(fragmentChance, profile.fragmentChance());
        assertEquals(shardChance, profile.shardChance());
    }
}

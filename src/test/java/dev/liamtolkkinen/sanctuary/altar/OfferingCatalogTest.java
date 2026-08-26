package dev.liamtolkkinen.sanctuary.altar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class OfferingCatalogTest {
    @Test
    void offeringsAreOrderedAndExperienceStrictlyIncreases() {
        List<OfferingCatalog.Offering> offerings = OfferingCatalog.all();
        assertEquals(12, offerings.size());
        int previousXp = 0;
        for (int index = 0; index < offerings.size(); index++) {
            var offering = offerings.get(index);
            assertEquals(index + 1, offering.number());
            assertTrue(offering.experiencePoints() > previousXp);
            previousXp = offering.experiencePoints();
        }
        assertEquals(250, offerings.getFirst().experiencePoints());
        assertEquals(3000, offerings.getLast().experiencePoints());
        assertEquals(
            19500,
            offerings.stream().mapToInt(OfferingCatalog.Offering::experiencePoints).sum()
        );
    }

    @Test
    void offeringChainMixesSanctuaryArtifactsAndMeaningfulVanillaItems() {
        var offerings = OfferingCatalog.all();
        assertEquals(
            ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT,
            offerings.get(0).ingredient().extendedItem()
        );
        assertEquals(Material.GOLDEN_APPLE, offerings.get(2).ingredient().material());
        assertEquals(Material.GHAST_TEAR, offerings.get(3).ingredient().material());
        assertEquals(
            ExtendedItemIds.WATCHERS_EYE,
            offerings.get(4).ingredient().extendedItem()
        );
        assertEquals(
            ExtendedItemIds.ATTUNEMENT_RELIC,
            offerings.get(5).ingredient().extendedItem()
        );
        assertEquals(Material.END_CRYSTAL, offerings.get(6).ingredient().material());
        assertEquals(Material.TOTEM_OF_UNDYING, offerings.get(9).ingredient().material());
        assertEquals(Material.NETHER_STAR, offerings.get(10).ingredient().material());
        assertEquals(
            ExtendedItemIds.CONSECRATED_KEYSTONE,
            offerings.get(11).ingredient().extendedItem()
        );
    }
}

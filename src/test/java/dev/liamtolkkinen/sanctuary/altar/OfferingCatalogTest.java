package dev.liamtolkkinen.sanctuary.altar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
        assertEquals(25, offerings.getFirst().experiencePoints());
        assertEquals(300, offerings.getLast().experiencePoints());
        assertEquals(1950, offerings.stream().mapToInt(OfferingCatalog.Offering::experiencePoints).sum());
    }
}

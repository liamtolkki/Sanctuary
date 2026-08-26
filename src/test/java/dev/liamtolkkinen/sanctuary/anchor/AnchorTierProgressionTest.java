package dev.liamtolkkinen.sanctuary.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import org.junit.jupiter.api.Test;

class AnchorTierProgressionTest {
    @Test
    void defaultMaximumRadiusProducesWholeNumberTierProgression() {
        assertEquals(20.0, AnchorTierProgression.radiusForTier(96.0, 1), 0.000001);
        assertEquals(39.0, AnchorTierProgression.radiusForTier(96.0, 2), 0.000001);
        assertEquals(58.0, AnchorTierProgression.radiusForTier(96.0, 3), 0.000001);
        assertEquals(77.0, AnchorTierProgression.radiusForTier(96.0, 4), 0.000001);
        assertEquals(96.0, AnchorTierProgression.radiusForTier(96.0, 5), 0.000001);
    }

    @Test
    void sanctuaryCoreUpgradesThroughTierFour() {
        assertEquals(
            ExtendedItemIds.SANCTUARY_CORE,
            AnchorTierProgression.requiredUpgradeItem(1)
        );
        assertEquals(
            ExtendedItemIds.SANCTUARY_CORE,
            AnchorTierProgression.requiredUpgradeItem(2)
        );
        assertEquals(
            ExtendedItemIds.SANCTUARY_CORE,
            AnchorTierProgression.requiredUpgradeItem(3)
        );
    }

    @Test
    void consecratedKeystoneIsRequiredForTierFive() {
        assertEquals(
            ExtendedItemIds.CONSECRATED_KEYSTONE,
            AnchorTierProgression.requiredUpgradeItem(4)
        );
    }

    @Test
    void tierFiveCannotUpgradeAgain() {
        assertThrows(
            IllegalStateException.class,
            () -> AnchorTierProgression.requiredUpgradeItem(5)
        );
        assertThrows(
            IllegalStateException.class,
            () -> AnchorTierProgression.nextTier(5)
        );
    }
}

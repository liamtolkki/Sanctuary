package dev.liamtolkkinen.sanctuary.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionEggStateTest {

    @Test
    void fullHealthHasNoDisplayedDamage() {
        assertEquals(0, CompanionEggState.displayDamage(20.0, 20.0));
    }

    @Test
    void displayedDamageTracksHealthLossProportionally() {
        assertEquals(250, CompanionEggState.displayDamage(15.0, 20.0));
        assertEquals(500, CompanionEggState.displayDamage(10.0, 20.0));
        assertEquals(750, CompanionEggState.displayDamage(5.0, 20.0));
    }

    @Test
    void displayedDamageNeverReachesBreakingValue() {
        int damage = CompanionEggState.displayDamage(0.0, 20.0);

        assertEquals(CompanionEggState.DISPLAY_MAX_DAMAGE - 1, damage);
        assertTrue(damage < CompanionEggState.DISPLAY_MAX_DAMAGE);
    }

    @Test
    void invalidHealthValuesFallBackToUndamagedDisplay() {
        assertEquals(0, CompanionEggState.displayDamage(Double.NaN, 20.0));
        assertEquals(0, CompanionEggState.displayDamage(10.0, 0.0));
    }
}

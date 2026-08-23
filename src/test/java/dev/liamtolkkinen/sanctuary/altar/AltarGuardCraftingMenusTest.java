package dev.liamtolkkinen.sanctuary.altar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class AltarGuardCraftingMenusTest {
    @Test
    void craftingGridIsShiftedOneColumnToTheRight() {
        assertArrayEquals(
            new int[] {
                11, 12, 13,
                20, 21, 22,
                29, 30, 31
            },
            AltarGuardCraftingMenus.craftingGridSlots()
        );
    }
}

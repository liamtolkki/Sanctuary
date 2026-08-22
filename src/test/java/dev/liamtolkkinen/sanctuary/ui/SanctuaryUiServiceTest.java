package dev.liamtolkkinen.sanctuary.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SanctuaryUiServiceTest {
    @Test
    void uniqueSanctuaryNameBecomesReadableSelector() {
        Sanctuary sanctuary = sanctuary("Liam's Sanctuary");
        assertEquals(
            "Liam's_Sanctuary",
            SanctuaryUiService.selectorLabel(sanctuary, List.of(sanctuary))
        );
    }

    @Test
    void selectorResolvesReadableNameAndUuid() {
        Sanctuary sanctuary = sanctuary("Main Base");
        List<Sanctuary> values = List.of(sanctuary);

        assertTrue(
            SanctuaryUiService.resolveSelector("Main_Base", values)
                .filter(value -> value.id().equals(sanctuary.id()))
                .isPresent()
        );
        assertTrue(
            SanctuaryUiService.resolveSelector(sanctuary.id().toString(), values)
                .filter(value -> value.id().equals(sanctuary.id()))
                .isPresent()
        );
    }


    @Test
    void sanctuaryNameIsTrimmedAndLimitedToThirtyTwoCharacters() {
        assertEquals("Seaside Keep", SanctuaryUiService.normalizeSanctuaryName("  Seaside Keep  "));
        assertThrows(IllegalArgumentException.class, () -> SanctuaryUiService.normalizeSanctuaryName("   "));
        assertThrows(
            IllegalArgumentException.class,
            () -> SanctuaryUiService.normalizeSanctuaryName("x".repeat(33))
        );
    }

    private static Sanctuary sanctuary(String name) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            name,
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            18.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }
}

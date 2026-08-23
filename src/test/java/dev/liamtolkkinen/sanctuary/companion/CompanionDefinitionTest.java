package dev.liamtolkkinen.sanctuary.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CompanionDefinitionTest {

    @Test
    void everyReleasedCompanionIdentityIsImplemented() {
        Set<ExtendedItemId> expected = Set.of(
            ExtendedItemIds.COMPANION_IRON_GOLEM,
            ExtendedItemIds.COMPANION_PILLAGER,
            ExtendedItemIds.COMPANION_SKELETON,
            ExtendedItemIds.COMPANION_PIGLIN_BRUTE,
            ExtendedItemIds.COMPANION_ENDERMAN,
            ExtendedItemIds.COMPANION_EVOKER,
            ExtendedItemIds.COMPANION_BABY_ZOMBIE,
            ExtendedItemIds.COMPANION_BLAZE,
            ExtendedItemIds.COMPANION_WARDEN,
            ExtendedItemIds.COMPANION_CREAKING,
            ExtendedItemIds.COMPANION_CREEPER,
            ExtendedItemIds.COMPANION_WITHER,
            ExtendedItemIds.COMPANION_DROWNED,
            ExtendedItemIds.COMPANION_GUARDIAN,
            ExtendedItemIds.COMPANION_ELDER_GUARDIAN,
            ExtendedItemIds.COMPANION_AXOLOTL,
            ExtendedItemIds.COMPANION_DOLPHIN
        );

        Set<ExtendedItemId> actual = CompanionDefinition.ALL.stream()
            .map(CompanionDefinition::itemId)
            .collect(Collectors.toSet());

        assertEquals(expected, actual);
        assertEquals(17, CompanionDefinition.ALL.size());
    }

    @Test
    void companionPersistentIdsAreUnique() {
        Set<String> ids = CompanionDefinition.ALL.stream()
            .map(CompanionDefinition::persistentId)
            .collect(Collectors.toSet());

        assertEquals(CompanionDefinition.ALL.size(), ids.size());
        assertTrue(ids.stream().allMatch(id -> id.startsWith("companion_")));
    }
}

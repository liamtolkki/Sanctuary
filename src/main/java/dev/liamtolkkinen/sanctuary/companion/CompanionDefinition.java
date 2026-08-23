package dev.liamtolkkinen.sanctuary.companion;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public record CompanionDefinition(
    ExtendedItemId itemId,
    String persistentId,
    String displayName,
    EntityType entityType,
    boolean baby
) {
    public static final List<CompanionDefinition> ALL = List.of(
        new CompanionDefinition(ExtendedItemIds.COMPANION_IRON_GOLEM, "companion_iron_golem", "Iron Golem Companion", EntityType.IRON_GOLEM, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_PILLAGER, "companion_pillager", "Pillager Companion", EntityType.PILLAGER, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_SKELETON, "companion_skeleton", "Skeleton Companion", EntityType.SKELETON, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_PIGLIN_BRUTE, "companion_piglin_brute", "Piglin Brute Companion", EntityType.PIGLIN_BRUTE, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_ENDERMAN, "companion_enderman", "Enderman Companion", EntityType.ENDERMAN, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_EVOKER, "companion_evoker", "Evoker Companion", EntityType.EVOKER, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_BABY_ZOMBIE, "companion_baby_zombie", "Baby Zombie Companion", EntityType.ZOMBIE, true),
        new CompanionDefinition(ExtendedItemIds.COMPANION_BLAZE, "companion_blaze", "Blaze Companion", EntityType.BLAZE, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_WARDEN, "companion_warden", "Warden Companion", EntityType.WARDEN, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_CREAKING, "companion_creaking", "Creaking Companion", EntityType.CREAKING, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_CREEPER, "companion_creeper", "Creeper Companion", EntityType.CREEPER, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_WITHER, "companion_wither", "Wither Companion", EntityType.WITHER, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_DROWNED, "companion_drowned", "Drowned Companion", EntityType.DROWNED, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_GUARDIAN, "companion_guardian", "Guardian Companion", EntityType.GUARDIAN, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_ELDER_GUARDIAN, "companion_elder_guardian", "Elder Guardian Companion", EntityType.ELDER_GUARDIAN, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_AXOLOTL, "companion_axolotl", "Axolotl Companion", EntityType.AXOLOTL, false),
        new CompanionDefinition(ExtendedItemIds.COMPANION_DOLPHIN, "companion_dolphin", "Dolphin Companion", EntityType.DOLPHIN, false)
    );

    public boolean requiresWaterSpawn() {
        return entityType == EntityType.GUARDIAN
            || entityType == EntityType.ELDER_GUARDIAN
            || entityType == EntityType.AXOLOTL
            || entityType == EntityType.DOLPHIN;
    }

    public static Optional<CompanionDefinition> fromItem(ItemStack item) {
        return ExtendedItems.getId(item).flatMap(CompanionDefinition::byItemId);
    }

    public static Optional<CompanionDefinition> byItemId(ExtendedItemId id) {
        return ALL.stream().filter(value -> value.itemId().equals(id)).findFirst();
    }

    public static Optional<CompanionDefinition> byPersistentId(String id) {
        return ALL.stream().filter(value -> value.persistentId().equals(id)).findFirst();
    }
}

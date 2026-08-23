package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.EntityType;

public record SentryDefinition(
    ExtendedItemId itemId,
    String persistentId,
    String displayName,
    EntityType entityType,
    double targetRadius,
    boolean baby
) {
    public static final List<SentryDefinition> ALL = List.of(
        new SentryDefinition(ExtendedItemIds.SENTRY_IRON_GOLEM, "sentry_iron_golem", "Iron Golem Sentry", EntityType.IRON_GOLEM, 18.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_PILLAGER, "sentry_pillager", "Pillager Sentry", EntityType.PILLAGER, 28.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_SKELETON, "sentry_skeleton", "Skeleton Sentry", EntityType.SKELETON, 28.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_PIGLIN_BRUTE, "sentry_piglin_brute", "Piglin Brute Sentry", EntityType.PIGLIN_BRUTE, 22.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_ENDERMAN, "sentry_enderman", "Enderman Sentry", EntityType.ENDERMAN, 40.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_EVOKER, "sentry_evoker", "Evoker Sentry", EntityType.EVOKER, 30.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_BABY_ZOMBIE, "sentry_baby_zombie", "Baby Zombie Sentry", EntityType.ZOMBIE, 20.0, true),
        new SentryDefinition(ExtendedItemIds.SENTRY_BLAZE, "sentry_blaze", "Blaze Sentry", EntityType.BLAZE, 28.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_WARDEN, "sentry_warden", "Warden Sentry", EntityType.WARDEN, 34.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_CREEPER, "sentry_creeper", "Creeper Sentry", EntityType.CREEPER, 24.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_WITHER, "sentry_wither", "Wither Sentry", EntityType.WITHER, 34.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_DROWNED, "sentry_drowned", "Drowned Sentry", EntityType.DROWNED, 24.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_GUARDIAN, "sentry_guardian", "Guardian Sentry", EntityType.GUARDIAN, 30.0, false),
        new SentryDefinition(ExtendedItemIds.SENTRY_ELDER_GUARDIAN, "sentry_elder_guardian", "Elder Guardian Sentry", EntityType.ELDER_GUARDIAN, 34.0, false)
    );

    public static Optional<SentryDefinition> byPersistentId(String id) {
        return ALL.stream().filter(value -> value.persistentId.equals(id)).findFirst();
    }
}

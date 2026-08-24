package dev.liamtolkkinen.sanctuary.loot;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

/** Defines the vanilla structure loot tables that can receive Sanctuary materials. */
public enum SanctuaryLootProfile {
    MINESHAFT(
        "mineshaft",
        "Abandoned Mineshaft",
        LootTables.ABANDONED_MINESHAFT,
        0.10,
        1,
        1,
        0.02
    ),
    STRONGHOLD_LIBRARY(
        "stronghold_library",
        "Stronghold Library",
        LootTables.STRONGHOLD_LIBRARY,
        0.25,
        1,
        2,
        0.07
    ),
    BURIED_TREASURE(
        "buried_treasure",
        "Buried Treasure",
        LootTables.BURIED_TREASURE,
        0.65,
        2,
        4,
        0.10
    ),
    BASTION_TREASURE(
        "bastion_treasure",
        "Bastion Treasure",
        LootTables.BASTION_TREASURE,
        0.85,
        2,
        4,
        0.25
    ),
    ANCIENT_CITY(
        "ancient_city",
        "Ancient City",
        LootTables.ANCIENT_CITY,
        0.45,
        1,
        3,
        0.20
    ),
    END_CITY(
        "end_city",
        "End City Treasure",
        LootTables.END_CITY_TREASURE,
        0.35,
        1,
        3,
        0.15
    );

    private static final List<SanctuaryLootProfile> ALL = List.of(values());

    private final String id;
    private final String displayName;
    private final LootTables lootTable;
    private final double fragmentChance;
    private final int minimumFragments;
    private final int maximumFragments;
    private final double shardChance;

    SanctuaryLootProfile(
        String id,
        String displayName,
        LootTables lootTable,
        double fragmentChance,
        int minimumFragments,
        int maximumFragments,
        double shardChance
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.lootTable = Objects.requireNonNull(lootTable, "lootTable");
        this.fragmentChance = validateChance(fragmentChance, "fragmentChance");
        if (minimumFragments < 1 || maximumFragments < minimumFragments) {
            throw new IllegalArgumentException(
                "Fragment quantity must be at least one and maximum must be >= minimum"
            );
        }
        this.minimumFragments = minimumFragments;
        this.maximumFragments = maximumFragments;
        this.shardChance = validateChance(shardChance, "shardChance");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public LootTables lootTableType() {
        return lootTable;
    }

    public LootTable lootTable() {
        return lootTable.getLootTable();
    }

    public NamespacedKey lootTableKey() {
        return lootTable.getKey();
    }

    public double fragmentChance() {
        return fragmentChance;
    }

    public int minimumFragments() {
        return minimumFragments;
    }

    public int maximumFragments() {
        return maximumFragments;
    }

    public double shardChance() {
        return shardChance;
    }

    public static List<SanctuaryLootProfile> all() {
        return ALL;
    }

    public static Optional<SanctuaryLootProfile> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(profile -> profile.id.equals(normalized))
            .findFirst();
    }

    public static Optional<SanctuaryLootProfile> fromLootTable(LootTable lootTable) {
        if (lootTable == null) {
            return Optional.empty();
        }
        NamespacedKey key = lootTable.getKey();
        return Arrays.stream(values())
            .filter(profile -> profile.lootTable.getKey().equals(key))
            .findFirst();
    }

    private static double validateChance(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
        return value;
    }
}

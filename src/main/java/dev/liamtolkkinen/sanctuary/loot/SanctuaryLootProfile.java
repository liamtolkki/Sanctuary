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
    DUNGEON(
        "dungeon",
        "Dungeon",
        LootTables.SIMPLE_DUNGEON,
        0.10,
        0.02
    ),
    MINESHAFT(
        "mineshaft",
        "Abandoned Mineshaft",
        LootTables.ABANDONED_MINESHAFT,
        0.10,
        0.02
    ),
    SHIPWRECK_TREASURE(
        "shipwreck_treasure",
        "Shipwreck Treasure",
        LootTables.SHIPWRECK_TREASURE,
        0.12,
        0.03
    ),
    RUINED_PORTAL(
        "ruined_portal",
        "Ruined Portal",
        LootTables.RUINED_PORTAL,
        0.12,
        0.03
    ),
    BURIED_TREASURE(
        "buried_treasure",
        "Buried Treasure",
        LootTables.BURIED_TREASURE,
        0.15,
        0.04
    ),
    DESERT_PYRAMID(
        "desert_pyramid",
        "Desert Pyramid",
        LootTables.DESERT_PYRAMID,
        0.15,
        0.04
    ),
    JUNGLE_TEMPLE(
        "jungle_temple",
        "Jungle Temple",
        LootTables.JUNGLE_TEMPLE,
        0.15,
        0.04
    ),
    PILLAGER_OUTPOST(
        "pillager_outpost",
        "Pillager Outpost",
        LootTables.PILLAGER_OUTPOST,
        0.15,
        0.04
    ),
    STRONGHOLD_CORRIDOR(
        "stronghold_corridor",
        "Stronghold Corridor",
        LootTables.STRONGHOLD_CORRIDOR,
        0.15,
        0.04
    ),
    STRONGHOLD_CROSSING(
        "stronghold_crossing",
        "Stronghold Crossing",
        LootTables.STRONGHOLD_CROSSING,
        0.15,
        0.04
    ),
    STRONGHOLD_LIBRARY(
        "stronghold_library",
        "Stronghold Library",
        LootTables.STRONGHOLD_LIBRARY,
        0.20,
        0.06
    ),
    TRIAL_CHAMBER_REWARD(
        "trial_chamber_reward",
        "Trial Chamber Reward",
        LootTables.TRIAL_CHAMBERS_REWARD,
        0.20,
        0.06
    ),
    NETHER_FORTRESS(
        "nether_fortress",
        "Nether Fortress",
        LootTables.NETHER_BRIDGE,
        0.20,
        0.06
    ),
    WOODLAND_MANSION(
        "woodland_mansion",
        "Woodland Mansion",
        LootTables.WOODLAND_MANSION,
        0.20,
        0.06
    ),
    BASTION_OTHER(
        "bastion_other",
        "Bastion Generic",
        LootTables.BASTION_OTHER,
        0.135,
        0.045
    ),
    BASTION_BRIDGE(
        "bastion_bridge",
        "Bastion Bridge",
        LootTables.BASTION_BRIDGE,
        0.135,
        0.045
    ),
    BASTION_HOGLIN_STABLE(
        "bastion_hoglin_stable",
        "Bastion Hoglin Stable",
        LootTables.BASTION_HOGLIN_STABLE,
        0.12,
        0.09
    ),
    BASTION_TREASURE(
        "bastion_treasure",
        "Bastion Treasure",
        LootTables.BASTION_TREASURE,
        0.27,
        0.217
    ),
    ANCIENT_CITY(
        "ancient_city",
        "Ancient City",
        LootTables.ANCIENT_CITY,
        0.30,
        0.15
    ),
    END_CITY(
        "end_city",
        "End City Treasure",
        LootTables.END_CITY_TREASURE,
        0.30,
        0.15
    );

    private static final List<SanctuaryLootProfile> ALL = List.of(values());

    private final String id;
    private final String displayName;
    private final LootTables lootTable;
    private final double fragmentChance;
    private final double shardChance;

    SanctuaryLootProfile(
        String id,
        String displayName,
        LootTables lootTable,
        double fragmentChance,
        double shardChance
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.lootTable = Objects.requireNonNull(lootTable, "lootTable");
        this.fragmentChance = validateChance(fragmentChance, "fragmentChance");
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

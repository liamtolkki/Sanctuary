package dev.liamtolkkinen.sanctuary.loot;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds Consecrated Shard materials to selected vanilla structure loot tables. */
public final class SanctuaryLootService implements Listener {
    private final NamespacedKey debugProfileKey;

    public SanctuaryLootService(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.debugProfileKey = new NamespacedKey(plugin, "debug_loot_profile");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        SanctuaryLootProfile profile = SanctuaryLootProfile
            .fromLootTable(event.getLootTable())
            .orElse(null);
        if (profile == null) {
            return;
        }

        List<ItemStack> loot = event.getLoot();
        if (profile == SanctuaryLootProfile.ANCIENT_CITY) {
            loot.removeIf(item -> item != null && item.getType() == Material.AMETHYST_SHARD);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < profile.fragmentChance()) {
            ItemStack fragments = ExtendedItems.create(
                ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT
            );
            fragments.setAmount(random.nextInt(
                profile.minimumFragments(),
                profile.maximumFragments() + 1
            ));
            loot.add(fragments);
        }
        if (random.nextDouble() < profile.shardChance()) {
            loot.add(ExtendedItems.create(ExtendedItemIds.CONSECRATED_SHARD));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDebugChestPlace(BlockPlaceEvent event) {
        ItemMeta itemMeta = event.getItemInHand().getItemMeta();
        if (itemMeta == null) {
            return;
        }
        String profileId = itemMeta.getPersistentDataContainer()
            .get(debugProfileKey, PersistentDataType.STRING);
        if (profileId == null) {
            return;
        }

        SanctuaryLootProfile profile = SanctuaryLootProfile.parse(profileId).orElse(null);
        if (profile == null) {
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof Chest chest)) {
            return;
        }

        chest.getInventory().clear();
        chest.setLootTable(profile.lootTable());
        chest.setSeed(ThreadLocalRandom.current().nextLong());
        chest.update(true, false);

        event.getPlayer().sendMessage(Component.text(
            "Placed Sanctuary debug loot chest: " + profile.displayName(),
            NamedTextColor.GOLD
        ));
        event.getPlayer().sendMessage(Component.text(
            String.format(
                "Fragment %.1f%% (%d-%d) | Shard %.1f%%",
                profile.fragmentChance() * 100.0,
                profile.minimumFragments(),
                profile.maximumFragments(),
                profile.shardChance() * 100.0
            ),
            NamedTextColor.GRAY
        ));
    }

    public ItemStack createDebugChest(SanctuaryLootProfile profile) {
        Objects.requireNonNull(profile, "profile");
        ItemStack chest = new ItemStack(Material.CHEST);
        chest.editMeta(meta -> {
            meta.displayName(Component.text(
                "Debug Loot: " + profile.displayName(),
                NamedTextColor.GOLD
            ));
            meta.lore(List.of(
                Component.text(
                    String.format(
                        "Fragment chance: %.1f%% (%d-%d)",
                        profile.fragmentChance() * 100.0,
                        profile.minimumFragments(),
                        profile.maximumFragments()
                    ),
                    NamedTextColor.AQUA
                ),
                Component.text(
                    String.format("Full shard chance: %.1f%%", profile.shardChance() * 100.0),
                    NamedTextColor.LIGHT_PURPLE
                ),
                Component.text(
                    "Place and open to roll the real loot table.",
                    NamedTextColor.GRAY
                )
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(
                debugProfileKey,
                PersistentDataType.STRING,
                profile.id()
            );
        });
        return chest;
    }

    public List<ItemStack> createAllDebugChests() {
        List<ItemStack> result = new ArrayList<>();
        for (SanctuaryLootProfile profile : SanctuaryLootProfile.all()) {
            result.add(createDebugChest(profile));
        }
        return List.copyOf(result);
    }
}

package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.Objects;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps ExtendedItems from falling back to their vanilla material behavior.
 *
 * Sanctuary recipes perform their own exact custom-item validation. Outside of
 * those recipes, a Sanctuary artifact must never be accepted merely because its
 * backing Material happens to satisfy a vanilla recipe or placeable block.
 */
final class SanctuaryItemUsageGuard implements Listener {
    private final String recipeNamespace;

    SanctuaryItemUsageGuard(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.recipeNamespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (shouldBlockCraft(event.getRecipe(), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (shouldBlockCraft(event.getRecipe(), event.getInventory().getMatrix())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (!(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)) {
            return;
        }
        if (shouldBlockCraft(event.getRecipe(), crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ExtendedItemId id = ExtendedItems.getId(event.getItemInHand()).orElse(null);
        if (id == null || isAllowedPlacedItem(id)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(
            "That Sanctuary item cannot be placed as its vanilla base block."
        );
    }

    boolean shouldBlockCraft(Recipe recipe, ItemStack[] matrix) {
        if (recipe instanceof Keyed keyed
            && keyed.getKey().getNamespace().equals(recipeNamespace)) {
            return false;
        }

        return containsExtendedItem(matrix);
    }

    static boolean containsExtendedItem(ItemStack[] items) {
        for (ItemStack item : items) {
            if (ExtendedItems.getId(item).isPresent()) {
                return true;
            }
        }
        return false;
    }

    static boolean isAllowedPlacedItem(ExtendedItemId id) {
        if (id.equals(ExtendedItemIds.SANCTUARY_BEACON)) {
            return true;
        }

        return id.persistentId().startsWith("sentry_");
    }
}

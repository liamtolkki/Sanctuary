package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.api.SanctuaryApi;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryRecipeDiscoveryService implements Listener {
    private static final long REFRESH_PERIOD_TICKS = 20L;

    private final JavaPlugin plugin;
    private final SanctuaryApi sanctuaryApi;
    private final SentryCraftingItemService craftingItems;
    private final NamespacedKey beaconMilestoneKey;

    public SentryRecipeDiscoveryService(
        JavaPlugin plugin,
        SanctuaryApi sanctuaryApi,
        SentryCraftingItemService craftingItems
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sanctuaryApi = Objects.requireNonNull(sanctuaryApi, "sanctuaryApi");
        this.craftingItems = Objects.requireNonNull(craftingItems, "craftingItems");
        this.beaconMilestoneKey = new NamespacedKey(plugin, "recipe_beacon_seen");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::refreshOnlinePlayers,
            1L,
            REFRESH_PERIOD_TICKS
        );

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restoreBeaconMilestone(player);
            refresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleRestoreAndRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        observeItem(player, event.getItem().getItemStack());
        unlockSeenRecipes(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        observeItem(event.getPlayer(), event.getItemDrop().getItemStack());
        unlockSeenRecipes(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!ExtendedItems.is(event.getItemInHand(), ExtendedItemIds.SANCTUARY_BEACON)) {
            return;
        }

        markBeaconMilestone(event.getPlayer());
        unlockSeenRecipes(event.getPlayer());
    }

    public void refresh(Player player) {
        Objects.requireNonNull(player, "player");

        for (ItemStack item : player.getInventory().getContents()) {
            observeItem(player, item);
        }

        unlockSeenRecipes(player);
    }

    private void refreshOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void scheduleRestoreAndRefresh(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            restoreBeaconMilestone(player);
            refresh(player);
        });
    }

    private void scheduleRefresh(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    private void restoreBeaconMilestone(Player player) {
        if (hasBeaconMilestone(player)) {
            return;
        }

        boolean ownsBeaconSanctuary = sanctuaryApi
            .getPlayerSanctuaries(player.getUniqueId())
            .stream()
            .anyMatch(sanctuary -> sanctuary.type() == SanctuaryType.BEACON);

        if (ownsBeaconSanctuary) {
            markBeaconMilestone(player);
        }
    }

    private void observeItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        if (ExtendedItems.is(item, ExtendedItemIds.SANCTUARY_BEACON)) {
            markBeaconMilestone(player);
        }

        for (SentryRecipeCatalog.CompanionRecipe recipe
            : SentryRecipeCatalog.companionRecipes())
        {
            if (matchesUnlockIngredient(item, recipe.unlockIngredient())) {
                markRecipeIngredientSeen(player, recipe.key());
            }
        }

        for (SentryRecipeCatalog.SentryConversion conversion
            : SentryRecipeCatalog.sentryConversions())
        {
            if (ExtendedItems.is(item, conversion.companion())) {
                markRecipeIngredientSeen(player, conversion.key());
            }
        }
    }

    private boolean matchesUnlockIngredient(
        ItemStack item,
        SentryRecipeCatalog.UnlockIngredient ingredient
    ) {
        if (ingredient.material() != null) {
            return item.getType() == ingredient.material();
        }
        return craftingItems.matchesSpecialIngredient(item, ingredient.special());
    }

    private void unlockSeenRecipes(Player player) {
        if (!hasBeaconMilestone(player)) {
            return;
        }

        for (SentryRecipeCatalog.CompanionRecipe recipe
            : SentryRecipeCatalog.companionRecipes())
        {
            discoverIfSeen(player, recipe.key());
        }

        for (SentryRecipeCatalog.SentryConversion conversion
            : SentryRecipeCatalog.sentryConversions())
        {
            discoverIfSeen(player, conversion.key());
        }
    }

    private void discoverIfSeen(Player player, String recipeKey) {
        if (!hasRecipeIngredientSeen(player, recipeKey)) {
            return;
        }
        player.discoverRecipe(new NamespacedKey(plugin, recipeKey));
    }

    private void markBeaconMilestone(Player player) {
        player.getPersistentDataContainer().set(
            beaconMilestoneKey,
            PersistentDataType.BYTE,
            (byte) 1
        );
    }

    private boolean hasBeaconMilestone(Player player) {
        Byte value = player.getPersistentDataContainer().get(
            beaconMilestoneKey,
            PersistentDataType.BYTE
        );
        return value != null && value == (byte) 1;
    }

    private void markRecipeIngredientSeen(Player player, String recipeKey) {
        player.getPersistentDataContainer().set(
            ingredientSeenKey(recipeKey),
            PersistentDataType.BYTE,
            (byte) 1
        );
    }

    private boolean hasRecipeIngredientSeen(Player player, String recipeKey) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Byte value = data.get(
            ingredientSeenKey(recipeKey),
            PersistentDataType.BYTE
        );
        return value != null && value == (byte) 1;
    }

    private NamespacedKey ingredientSeenKey(String recipeKey) {
        return new NamespacedKey(plugin, "recipe_seen_" + recipeKey);
    }
}

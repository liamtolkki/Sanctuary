package dev.liamtolkkinen.sanctuary.crafting;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctuaryRecipeService implements Listener {
    private final JavaPlugin plugin;
    private final AnchorItemService anchorItemService;
    private final SanctuaryRecipeValidator validator = new SanctuaryRecipeValidator();
    private final Map<NamespacedKey, SanctuaryRecipeCatalog.RecipeDefinition> recipesByKey =
        new LinkedHashMap<>();

    public SanctuaryRecipeService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.anchorItemService = new AnchorItemService(plugin);
    }

    public void registerAll() {
        recipesByKey.clear();
        for (var definition : SanctuaryRecipeCatalog.shapelessRecipes()) {
            registerShapeless(definition);
        }
        for (var definition : SanctuaryRecipeCatalog.shapedRecipes()) {
            registerShaped(definition);
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(
            new SanctuaryItemUsageGuard(plugin),
            plugin
        );
        SanctuaryProgressionDebugCommand.register(plugin);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            showProgressionRecipes(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        var definition = definitionFor(event.getRecipe());
        if (definition == null) {
            return;
        }
        if (!validator.matches(definition, event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(createResult(definition));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        var definition = definitionFor(event.getRecipe());
        if (definition == null) {
            return;
        }
        if (!validator.matches(definition, event.getInventory().getMatrix())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            return;
        }

        if (definition.result().equals(ExtendedItemIds.SANCTUARY_BEACON)
            && event.isShiftClick()) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(
                "Craft Sanctuary Beacons one at a time so each Beacon receives a unique anchor identity."
            );
            return;
        }

        event.setCurrentItem(createResult(definition));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        var definition = definitionFor(event.getRecipe());
        if (definition == null) {
            return;
        }
        if (!(event.getBlock().getState() instanceof Crafter crafter)) {
            event.setCancelled(true);
            return;
        }
        ItemStack[] matrix = crafter.getInventory().getContents();
        if (!validator.matches(definition, matrix)) {
            event.setCancelled(true);
            return;
        }
        event.setResult(createResult(definition));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRecipeBookClick(PlayerRecipeBookClickEvent event) {
        var definition = recipesByKey.get(event.getRecipe());
        if (definition == null) {
            return;
        }

        // Cancel vanilla placement completely. The client only understands the
        // backing materials for ExtendedItems, so Sanctuary fills the recipe
        // with the exact PDC-tagged ingredients instead.
        event.setCancelled(true);
        event.setMakeAll(false);
        plugin.getServer().getScheduler().runTask(
            plugin,
            () -> fillExactRecipeFromBook(event.getPlayer(), definition)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(
            plugin,
            () -> showProgressionRecipes(event.getPlayer())
        );
    }

    private void registerShaped(SanctuaryRecipeCatalog.ShapedRecipeDefinition definition) {
        NamespacedKey key = new NamespacedKey(plugin, definition.key());
        ShapedRecipe recipe = new ShapedRecipe(key, createResult(definition));
        recipe.shape(definition.shape().toArray(String[]::new));
        for (var entry : definition.ingredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), registrationChoice(entry.getValue()));
        }
        replaceRecipe(key, recipe, definition);
    }

    private void registerShapeless(SanctuaryRecipeCatalog.ShapelessRecipeDefinition definition) {
        NamespacedKey key = new NamespacedKey(plugin, definition.key());
        ShapelessRecipe recipe = new ShapelessRecipe(
            key,
            createResult(definition)
        );
        for (var ingredient : definition.ingredients()) {
            recipe.addIngredient(registrationChoice(ingredient));
        }
        replaceRecipe(key, recipe, definition);
    }

    static RecipeChoice registrationChoice(SanctuaryRecipeCatalog.Ingredient ingredient) {
        if (ingredient.material() != null) {
            return new RecipeChoice.MaterialChoice(ingredient.material());
        }
        return new RecipeChoice.ExactChoice(
            ExtendedItems.create(ingredient.extendedItem())
        );
    }

    ItemStack createResult(SanctuaryRecipeCatalog.RecipeDefinition definition) {
        if (definition.result().equals(ExtendedItemIds.SANCTUARY_BEACON)) {
            return anchorItemService.createUnboundBeacon();
        }

        ItemStack result = ExtendedItems.create(definition.result());
        if (definition.result().equals(ExtendedItemIds.SEAL_OF_KEEPING)
            && result.getType() == Material.ENDER_CHEST) {
            // Sanctuary is still pinned to ExtendedItems alpha.7 while the
            // alpha.8 Seal definition is awaiting release. Keep the PDC
            // identity but use the corrected backing material immediately.
            result.setType(Material.SHULKER_SHELL);
            result.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return result;
    }

    private void fillExactRecipeFromBook(
        Player player,
        SanctuaryRecipeCatalog.RecipeDefinition definition
    ) {
        if (!(player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting)) {
            return;
        }

        ItemStack[] current = crafting.getMatrix();
        returnMatrixToPlayer(player, current);

        ItemStack[] desired = new ItemStack[current.length];
        List<ItemStack> taken = new ArrayList<>();
        boolean complete;

        if (definition instanceof SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped) {
            complete = fillShaped(player, shaped, desired, taken);
        } else if (definition instanceof SanctuaryRecipeCatalog.ShapelessRecipeDefinition shapeless) {
            complete = fillShapeless(player, shapeless, desired, taken);
        } else {
            complete = false;
        }

        if (!complete) {
            rollbackTaken(player, taken);
            crafting.setMatrix(new ItemStack[current.length]);
            crafting.setResult(null);
            player.updateInventory();
            player.sendMessage(
                ChatColor.YELLOW + "You do not have the exact Sanctuary ingredients for that recipe."
            );
            return;
        }

        crafting.setMatrix(desired);
        crafting.setResult(createResult(definition));
        player.updateInventory();
    }

    private boolean fillShaped(
        Player player,
        SanctuaryRecipeCatalog.ShapedRecipeDefinition definition,
        ItemStack[] desired,
        List<ItemStack> taken
    ) {
        if (desired.length < 9) {
            player.sendMessage(ChatColor.YELLOW + "Use a crafting table for this Sanctuary recipe.");
            return false;
        }

        for (int row = 0; row < 3; row++) {
            String shapeRow = definition.shape().get(row);
            for (int column = 0; column < 3; column++) {
                char symbol = shapeRow.charAt(column);
                if (symbol == ' ') {
                    continue;
                }
                ItemStack ingredient = takeIngredient(
                    player.getInventory(),
                    definition.ingredients().get(symbol)
                );
                if (ingredient == null) {
                    return false;
                }
                desired[row * 3 + column] = ingredient;
                taken.add(ingredient.clone());
            }
        }
        return true;
    }

    private boolean fillShapeless(
        Player player,
        SanctuaryRecipeCatalog.ShapelessRecipeDefinition definition,
        ItemStack[] desired,
        List<ItemStack> taken
    ) {
        if (definition.ingredients().size() > desired.length) {
            return false;
        }

        int slot = 0;
        for (var ingredientDefinition : definition.ingredients()) {
            ItemStack ingredient = takeIngredient(player.getInventory(), ingredientDefinition);
            if (ingredient == null) {
                return false;
            }
            desired[slot++] = ingredient;
            taken.add(ingredient.clone());
        }
        return true;
    }

    private ItemStack takeIngredient(
        PlayerInventory inventory,
        SanctuaryRecipeCatalog.Ingredient ingredient
    ) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack candidate = storage[slot];
            if (!matchesIngredient(candidate, ingredient)) {
                continue;
            }

            ItemStack taken = candidate.clone();
            taken.setAmount(1);
            if (candidate.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                candidate.setAmount(candidate.getAmount() - 1);
                inventory.setItem(slot, candidate);
            }
            return taken;
        }
        return null;
    }

    private boolean matchesIngredient(
        ItemStack item,
        SanctuaryRecipeCatalog.Ingredient ingredient
    ) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (ingredient.extendedItem() != null) {
            return ExtendedItems.is(item, ingredient.extendedItem());
        }
        return item.getType() == ingredient.material()
            && ExtendedItems.getId(item).isEmpty();
    }

    private void returnMatrixToPlayer(Player player, ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void rollbackTaken(Player player, List<ItemStack> taken) {
        for (ItemStack item : taken) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void showProgressionRecipes(Player player) {
        player.discoverRecipes(recipesByKey.keySet());
    }

    private void replaceRecipe(
        NamespacedKey key,
        Recipe recipe,
        SanctuaryRecipeCatalog.RecipeDefinition definition
    ) {
        plugin.getServer().removeRecipe(key);
        if (!plugin.getServer().addRecipe(recipe)) {
            throw new IllegalStateException("Failed to register Sanctuary recipe " + key);
        }
        recipesByKey.put(key, definition);
    }

    private SanctuaryRecipeCatalog.RecipeDefinition definitionFor(Recipe recipe) {
        return recipe instanceof Keyed keyed ? recipesByKey.get(keyed.getKey()) : null;
    }
}

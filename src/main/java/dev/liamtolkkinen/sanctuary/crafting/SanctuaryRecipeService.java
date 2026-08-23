package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Keyed;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers only the bootstrap Divine Altar recipe. All other progression crafting lives in the altar UI. */
public final class SanctuaryRecipeService implements Listener {
    private final JavaPlugin plugin;
    private final SanctuaryRecipeValidator validator = new SanctuaryRecipeValidator();
    private final Map<NamespacedKey, SanctuaryRecipeCatalog.RecipeDefinition> recipesByKey =
        new LinkedHashMap<>();
    private NamespacedKey altarRecipeKey;

    public SanctuaryRecipeService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void registerAll() {
        recipesByKey.clear();
        var altar = SanctuaryRecipeCatalog.findByResult(ExtendedItemIds.DIVINE_ALTAR)
            .filter(SanctuaryRecipeCatalog.ShapedRecipeDefinition.class::isInstance)
            .map(SanctuaryRecipeCatalog.ShapedRecipeDefinition.class::cast)
            .orElseThrow(() -> new IllegalStateException("Divine Altar recipe is missing from the catalog"));
        registerShaped(altar);
        altarRecipeKey = new NamespacedKey(plugin, altar.key());

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(
            new SanctuaryItemUsageGuard(plugin),
            plugin
        );
        SanctuaryProgressionDebugCommand.register(plugin);
        SanctuaryRelicDebugCommand.register(plugin);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            hideAltarRecipe(player);
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
        event.setCurrentItem(createResult(definition));
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> hideAltarRecipe(player));
        }
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(
            plugin,
            () -> hideAltarRecipe(event.getPlayer())
        );
    }

    private void registerShaped(SanctuaryRecipeCatalog.ShapedRecipeDefinition definition) {
        NamespacedKey key = new NamespacedKey(plugin, definition.key());
        ShapedRecipe recipe = new ShapedRecipe(key, createResult(definition));
        recipe.shape(SanctuaryRecipeCatalog.compactShape(definition).toArray(String[]::new));
        for (var entry : definition.ingredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), registrationChoice(entry.getValue()));
        }
        replaceRecipe(key, recipe, definition);
    }

    static RecipeChoice registrationChoice(SanctuaryRecipeCatalog.Ingredient ingredient) {
        if (ingredient.material() != null) {
            return new RecipeChoice.MaterialChoice(ingredient.material());
        }
        return new RecipeChoice.ExactChoice(ExtendedItems.create(ingredient.extendedItem()));
    }

    ItemStack createResult(SanctuaryRecipeCatalog.RecipeDefinition definition) {
        return ExtendedItems.create(definition.result());
    }

    private void hideAltarRecipe(Player player) {
        if (altarRecipeKey != null) {
            player.undiscoverRecipe(altarRecipeKey);
        }
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

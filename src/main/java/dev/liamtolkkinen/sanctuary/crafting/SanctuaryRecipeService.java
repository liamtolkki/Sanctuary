package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
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
        if (definition.result().equals(ExtendedItemIds.SEAL_OF_KEEPING)) {
            result.editMeta(meta -> {
                meta.setEnchantmentGlintOverride(true);
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            });
        }
        return result;
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

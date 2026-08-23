package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.api.SanctuaryApi;
import dev.liamtolkkinen.sanctuary.companion.CompanionDebugCommand;
import dev.liamtolkkinen.sanctuary.companion.CompanionService;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryRecipeService {
    private final JavaPlugin plugin;
    private final SentryCraftingItemService craftingItems;

    public SentryRecipeService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.craftingItems = new SentryCraftingItemService(plugin);
    }

    public void registerAll() {
        for (SentryRecipeCatalog.CompanionRecipe definition
            : SentryRecipeCatalog.companionRecipes())
        {
            registerCompanionRecipe(definition);
        }

        for (SentryRecipeCatalog.SentryConversion definition
            : SentryRecipeCatalog.sentryConversions())
        {
            registerSentryConversion(definition);
        }

        SanctuaryApi sanctuaryApi = plugin.getServer()
            .getServicesManager()
            .load(SanctuaryApi.class);
        if (sanctuaryApi == null) {
            throw new IllegalStateException(
                "Sanctuary API must be registered before sentry recipe discovery starts"
            );
        }

        new SentryRecipeDiscoveryService(
            plugin,
            sanctuaryApi,
            craftingItems
        ).start();

        CompanionDebugCommand.register(plugin);
        new CompanionService(plugin).start();
    }

    public SentryCraftingItemService craftingItems() {
        return craftingItems;
    }

    private void registerCompanionRecipe(
        SentryRecipeCatalog.CompanionRecipe definition
    ) {
        NamespacedKey key = new NamespacedKey(plugin, definition.key());
        ShapelessRecipe recipe = new ShapelessRecipe(
            key,
            ExtendedItems.create(definition.result())
        );

        for (SentryRecipeCatalog.Ingredient ingredient : definition.ingredients()) {
            if (ingredient.material() != null) {
                recipe.addIngredient(ingredient.count(), ingredient.material());
                continue;
            }

            ItemStack exactItem =
                craftingItems.createSpecialIngredient(ingredient.special());
            RecipeChoice.ExactChoice exactChoice =
                new RecipeChoice.ExactChoice(exactItem);

            for (int i = 0; i < ingredient.count(); i++) {
                recipe.addIngredient(exactChoice);
            }
        }

        replaceRecipe(key, recipe);
    }

    private void registerSentryConversion(
        SentryRecipeCatalog.SentryConversion definition
    ) {
        NamespacedKey key = new NamespacedKey(plugin, definition.key());
        ShapelessRecipe recipe = new ShapelessRecipe(
            key,
            ExtendedItems.create(definition.sentry())
        );

        recipe.addIngredient(
            new RecipeChoice.ExactChoice(
                ExtendedItems.create(definition.companion())
            )
        );
        recipe.addIngredient(definition.postMaterial());

        replaceRecipe(key, recipe);
    }

    private void replaceRecipe(
        NamespacedKey key,
        ShapelessRecipe recipe
    ) {
        plugin.getServer().removeRecipe(key);
        if (!plugin.getServer().addRecipe(recipe)) {
            throw new IllegalStateException(
                "Failed to register Sanctuary recipe " + key
            );
        }
    }
}

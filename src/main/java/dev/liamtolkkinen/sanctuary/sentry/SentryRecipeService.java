package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.advancement.SanctuaryAdvancementService;
import dev.liamtolkkinen.sanctuary.crafting.SanctuaryRecipeService;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the Sanctuary bootstrap recipes and advancement hooks.
 * Companion and sentry recipes are intentionally crafted through the Divine Altar.
 */
public final class SentryRecipeService {
    private final JavaPlugin plugin;

    public SentryRecipeService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void registerAll() {
        new SanctuaryRecipeService(plugin).registerAll();

        for (SentryRecipeCatalog.CompanionRecipe definition
            : SentryRecipeCatalog.companionRecipes())
        {
            plugin.getServer().removeRecipe(
                new NamespacedKey(plugin, definition.key())
            );
        }

        for (SentryRecipeCatalog.SentryConversion definition
            : SentryRecipeCatalog.sentryConversions())
        {
            plugin.getServer().removeRecipe(
                new NamespacedKey(plugin, definition.key())
            );
        }

        new SanctuaryAdvancementService(plugin).start();
    }
}

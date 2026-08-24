package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.advancement.SanctuaryAdvancementService;
import dev.liamtolkkinen.sanctuary.altar.DivineRelicListener;
import dev.liamtolkkinen.sanctuary.crafting.SanctuaryRecipeService;
import dev.liamtolkkinen.sanctuary.loot.SanctuaryDebugLootCommand;
import dev.liamtolkkinen.sanctuary.loot.SanctuaryLootService;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers the Sanctuary bootstrap recipes, progression hooks, and shared content services.
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
        plugin.getServer().getPluginManager().registerEvents(
            new DivineRelicListener(),
            plugin
        );

        SanctuaryLootService lootService = new SanctuaryLootService(plugin);
        plugin.getServer().getPluginManager().registerEvents(lootService, plugin);

        SanctuaryDebugLootCommand debugLootCommand =
            new SanctuaryDebugLootCommand(lootService);
        var command = Objects.requireNonNull(
            plugin.getCommand("sanctuarydebugloot"),
            "sanctuarydebugloot command is missing from plugin.yml"
        );
        command.setExecutor(debugLootCommand);
        command.setTabCompleter(debugLootCommand);
    }
}

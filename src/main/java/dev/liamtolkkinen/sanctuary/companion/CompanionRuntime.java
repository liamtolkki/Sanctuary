package dev.liamtolkkinen.sanctuary.companion;

import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.sanctuary.sentry.ManagedElderGuardianEffectListener;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompanionRuntime {
    private CompanionRuntime() {
    }

    public static void start(JavaPlugin plugin, ExtendedUI ui) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ui, "ui");

        CompanionService service = new CompanionService(plugin);
        CompanionEggState eggState = new CompanionEggState(plugin);
        CompanionUiService uiService = new CompanionUiService(
            plugin,
            ui,
            service,
            eggState,
            plugin.getLogger()
        );

        plugin.getServer().getPluginManager().registerEvents(
            new CompanionListener(service, uiService, eggState, plugin, plugin.getLogger()),
            plugin
        );
        plugin.getServer().getPluginManager().registerEvents(
            new ManagedElderGuardianEffectListener(plugin),
            plugin
        );
        new CompanionTask(service, plugin.getLogger()).start(plugin);
        CompanionDebugCommand.register(plugin);
    }
}

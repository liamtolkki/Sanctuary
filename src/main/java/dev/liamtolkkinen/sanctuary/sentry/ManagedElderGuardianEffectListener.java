package dev.liamtolkkinen.sanctuary.sentry;

import io.papermc.paper.event.entity.ElderGuardianAppearanceEvent;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Prevents Sanctuary-managed Elder Guardians from applying their vanilla Mining Fatigue pulse. */
public final class ManagedElderGuardianEffectListener implements Listener {
    private final NamespacedKey sentryIdKey;
    private final NamespacedKey companionIdKey;

    public ManagedElderGuardianEffectListener(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.sentryIdKey = new NamespacedKey(plugin, "sentry_id");
        this.companionIdKey = new NamespacedKey(plugin, "companion_id");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onElderGuardianAppearance(ElderGuardianAppearanceEvent event) {
        var data = event.getEntity().getPersistentDataContainer();
        if (data.has(sentryIdKey, PersistentDataType.STRING)
            || data.has(companionIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}

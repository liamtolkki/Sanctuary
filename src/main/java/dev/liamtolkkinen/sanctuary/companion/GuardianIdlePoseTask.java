package dev.liamtolkkinen.sanctuary.companion;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Guardian;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps stationary managed Guardians from being frozen in the last pitch used
 * by their swimming movement controller.
 *
 * Guardian movement intentionally pitches the model toward its destination.
 * Sanctuary freezes idle sentries and companions after they reach their post
 * or formation position, so without this reset a Guardian can remain visually
 * nose-down indefinitely. Active swimming and laser targeting are left alone.
 */
public final class GuardianIdlePoseTask implements Runnable {
    private static final long UPDATE_PERIOD_TICKS = 2L;

    private final JavaPlugin plugin;

    public GuardianIdlePoseTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 2L, UPDATE_PERIOD_TICKS);
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Guardian guardian : world.getEntitiesByClass(Guardian.class)) {
                if (!isManaged(guardian)
                    || guardian.isDead()
                    || guardian.isMoving()
                    || guardian.getTarget() != null) {
                    continue;
                }

                // A stale laser target can otherwise leave the beam visual active
                // after Sanctuary has put the Guardian back into its idle state.
                if (guardian.hasLaser()) {
                    guardian.setLaser(false);
                }

                if (Math.abs(guardian.getPitch()) > 0.01f) {
                    guardian.setRotation(guardian.getYaw(), 0.0f);
                }
            }
        }
    }

    private boolean isManaged(Guardian guardian) {
        var data = guardian.getPersistentDataContainer();
        return data.has(pluginKey("sentry_id"), PersistentDataType.STRING)
            || data.has(pluginKey("companion_id"), PersistentDataType.STRING);
    }

    private org.bukkit.NamespacedKey pluginKey(String key) {
        return new org.bukkit.NamespacedKey(plugin, key);
    }
}

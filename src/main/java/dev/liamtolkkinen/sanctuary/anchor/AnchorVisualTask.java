package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/** Lightweight persistent visuals for non-Beacon Sanctuary anchors. */
public final class AnchorVisualTask implements Runnable {
    private final SanctuaryAnchorRepository anchorRepository;
    private final Logger logger;
    private long tick;

    public AnchorVisualTask(SanctuaryAnchorRepository anchorRepository, Logger logger) {
        this.anchorRepository = Objects.requireNonNull(anchorRepository, "anchorRepository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void start(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 10L);
    }

    @Override
    public void run() {
        tick++;
        for (World world : Bukkit.getWorlds()) {
            try {
                for (SanctuaryAnchor anchor : anchorRepository.findActiveInWorld(world.getName())) {
                    if (anchor.type() != SanctuaryType.CONDUIT || anchor.position().isEmpty()) {
                        continue;
                    }
                    drawConduit(world, anchor.position().orElseThrow());
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to render Sanctuary Conduit visuals", exception);
            }
        }
    }

    private void drawConduit(World world, SanctuaryPosition position) {
        if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return;
        }

        double centerX = position.x() + 0.5;
        double centerY = position.y() + 0.55;
        double centerZ = position.z() + 0.5;
        double phase = tick * 0.16;

        for (int index = 0; index < 4; index++) {
            double angle = phase + index * (Math.PI / 2.0);
            Location orbit = new Location(
                world,
                centerX + Math.cos(angle) * 0.72,
                centerY + 0.12 * Math.sin(phase * 0.7 + index),
                centerZ + Math.sin(angle) * 0.72
            );
            world.spawnParticle(Particle.NAUTILUS, orbit, 1, 0.02, 0.02, 0.02, 0.0);
        }

        Location center = new Location(world, centerX, centerY, centerZ);
        world.spawnParticle(Particle.BUBBLE, center, 2, 0.32, 0.2, 0.32, 0.01);
        if (tick % 4L == 0L) {
            world.spawnParticle(Particle.END_ROD, center, 1, 0.22, 0.18, 0.22, 0.0);
        }
    }
}

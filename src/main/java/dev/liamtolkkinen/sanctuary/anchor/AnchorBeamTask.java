package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Renders a persistent Sanctuary core beam without requiring a vanilla Beacon pyramid.
 */
public final class AnchorBeamTask implements Runnable {
    private static final Particle.DustOptions CORE_BEAM =
        new Particle.DustOptions(Color.fromRGB(116, 228, 255), 1.35f);
    private static final Particle.DustOptions OUTER_BEAM =
        new Particle.DustOptions(Color.fromRGB(230, 249, 255), 1.8f);
    private static final double VERTICAL_STEP = 1.25;
    private static final long UPDATE_PERIOD_TICKS = 5L;

    private final SanctuaryRepository repository;
    private final Logger logger;

    public AnchorBeamTask(SanctuaryRepository repository, Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 10L, UPDATE_PERIOD_TICKS);
    }

    @Override
    public void run() {
        try {
            for (Sanctuary sanctuary : repository.findAll()) {
                if (sanctuary.state() != SanctuaryState.ACTIVE || sanctuary.position().isEmpty()) {
                    continue;
                }
                renderBeam(sanctuary.position().orElseThrow());
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed Sanctuary Beacon beam tick", exception);
        }
    }

    private void renderBeam(SanctuaryPosition position) {
        World world = Bukkit.getWorld(position.world());
        if (world == null || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return;
        }
        if (world.getBlockAt(position.x(), position.y(), position.z()).getType() != Material.BEACON) {
            return;
        }

        double x = position.x() + 0.5;
        double z = position.z() + 0.5;
        double baseY = position.y() + 1.05;

        world.spawnParticle(
            Particle.END_ROD,
            x,
            baseY + 0.15,
            z,
            5,
            0.14,
            0.18,
            0.14,
            0.005,
            null,
            true
        );

        for (double y = baseY; y < world.getMaxHeight(); y += VERTICAL_STEP) {
            world.spawnParticle(
                Particle.DUST,
                x,
                y,
                z,
                2,
                0.05,
                0.22,
                0.05,
                0.0,
                CORE_BEAM,
                true
            );
            world.spawnParticle(
                Particle.DUST,
                x,
                y,
                z,
                1,
                0.12,
                0.28,
                0.12,
                0.0,
                OUTER_BEAM,
                true
            );
        }
    }
}

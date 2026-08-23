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
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Adds a restrained particle treatment around active Sanctuary cores.
 *
 * <p>The vanilla Beacon beam is left entirely to vanilla mechanics, so a normal Beacon base can
 * provide the beam without any server-internal state manipulation.</p>
 */
public final class AnchorBeamTask implements Runnable {
    private static final Particle.DustOptions CORE_GLOW =
        new Particle.DustOptions(Color.fromRGB(116, 228, 255), 1.15f);
    private static final Particle.DustOptions BASE_GLOW =
        new Particle.DustOptions(Color.fromRGB(210, 246, 255), 0.9f);
    private static final long UPDATE_PERIOD_TICKS = 10L;

    private static final double[][] BASE_POINTS = {
        {-1.0, -1.0},
        {-1.0, 0.0},
        {-1.0, 1.0},
        {0.0, -1.0},
        {0.0, 1.0},
        {1.0, -1.0},
        {1.0, 0.0},
        {1.0, 1.0}
    };

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
                renderCore(sanctuary.position().orElseThrow());
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed Sanctuary Beacon particle tick", exception);
        }
    }

    private void renderCore(SanctuaryPosition position) {
        World world = Bukkit.getWorld(position.world());
        if (world == null || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return;
        }

        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        if (block.getType() != Material.BEACON) {
            return;
        }

        double centerX = position.x() + 0.5;
        double centerZ = position.z() + 0.5;
        double baseY = position.y();

        world.spawnParticle(
            Particle.END_ROD,
            centerX,
            baseY + 1.15,
            centerZ,
            2,
            0.12,
            0.12,
            0.12,
            0.003,
            null,
            true
        );
        world.spawnParticle(
            Particle.DUST,
            centerX,
            baseY + 1.08,
            centerZ,
            3,
            0.18,
            0.08,
            0.18,
            0.0,
            CORE_GLOW,
            true
        );

        for (double[] point : BASE_POINTS) {
            double x = centerX + point[0];
            double z = centerZ + point[1];
            world.spawnParticle(
                Particle.DUST,
                x,
                baseY - 0.02,
                z,
                1,
                0.06,
                0.03,
                0.06,
                0.0,
                BASE_GLOW,
                true
            );
        }

        world.spawnParticle(
            Particle.ENCHANTED_HIT,
            centerX,
            baseY + 0.2,
            centerZ,
            3,
            1.05,
            0.06,
            1.05,
            0.0,
            null,
            true
        );
    }
}

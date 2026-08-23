package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps active Sanctuary cores visually powered without requiring a vanilla Beacon pyramid.
 *
 * <p>The particle aura is Sanctuary-specific. The actual Beacon beam is forced by setting the
 * live Paper Beacon block entity to level 1 and notifying Paper that the block entity changed.
 * Reflection keeps the plugin compiled against the public Paper API instead of server-internal
 * classes.</p>
 */
public final class AnchorBeamTask implements Runnable {
    private static final Particle.DustOptions CORE_BEAM =
        new Particle.DustOptions(Color.fromRGB(116, 228, 255), 1.35f);
    private static final Particle.DustOptions OUTER_BEAM =
        new Particle.DustOptions(Color.fromRGB(230, 249, 255), 1.8f);
    private static final double VERTICAL_STEP = 1.25;
    private static final long UPDATE_PERIOD_TICKS = 5L;
    private static final int FORCED_BEACON_LEVEL = 1;

    private final SanctuaryRepository repository;
    private final Logger logger;
    private boolean vanillaBeamReflectionWarningLogged;

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
            logger.log(Level.WARNING, "Failed Sanctuary Beacon beam tick", exception);
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

        forceVanillaBeaconBeam(block);
        renderParticleAura(world, position);
    }

    private void renderParticleAura(World world, SanctuaryPosition position) {
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

    private void forceVanillaBeaconBeam(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Beacon)) {
            return;
        }

        try {
            Method getBlockEntity = state.getClass().getMethod("getBlockEntity");
            Object blockEntity = getBlockEntity.invoke(state);
            if (blockEntity == null) {
                return;
            }

            Field levels = findField(blockEntity.getClass(), "levels");
            if (levels.getInt(blockEntity) != FORCED_BEACON_LEVEL) {
                levels.setInt(blockEntity, FORCED_BEACON_LEVEL);
            }

            Method setChanged = findZeroArgumentMethod(blockEntity.getClass(), "setChanged");
            setChanged.invoke(blockEntity);

            Method getBlockPos = findZeroArgumentMethod(blockEntity.getClass(), "getBlockPos");
            Object blockPos = getBlockPos.invoke(blockEntity);
            Method getLevel = findZeroArgumentMethod(blockEntity.getClass(), "getLevel");
            Object level = getLevel.invoke(blockEntity);
            if (level == null || blockPos == null) {
                return;
            }

            Method getChunkSource = findZeroArgumentMethod(level.getClass(), "getChunkSource");
            Object chunkSource = getChunkSource.invoke(level);
            if (chunkSource == null) {
                return;
            }

            Method blockChanged = Arrays.stream(chunkSource.getClass().getMethods())
                .filter(method -> method.getName().equals("blockChanged"))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(blockPos.getClass()))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("blockChanged(BlockPos)"));
            blockChanged.invoke(chunkSource, blockPos);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logVanillaBeamReflectionWarning(exception);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findZeroArgumentMethod(Class<?> type, String name) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name + "()");
    }

    private void logVanillaBeamReflectionWarning(Exception exception) {
        if (vanillaBeamReflectionWarningLogged) {
            return;
        }
        vanillaBeamReflectionWarningLogged = true;
        Throwable cause = exception instanceof InvocationTargetException && exception.getCause() != null
            ? exception.getCause()
            : exception;
        logger.log(
            Level.WARNING,
            "Could not force the vanilla Sanctuary Beacon beam. The particle core effect will continue.",
            cause
        );
    }
}

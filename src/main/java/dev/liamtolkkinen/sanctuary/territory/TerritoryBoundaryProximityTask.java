package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TerritoryBoundaryProximityTask implements Runnable {
    private final SanctuaryRepository repository;
    private final TerritoryBoundaryService boundaryService;
    private final BooleanSupplier enabled;
    private final DoubleSupplier minimumDistance;
    private final DoubleSupplier maximumDistance;
    private final DoubleSupplier horizontalSpacing;
    private final DoubleSupplier verticalSpacing;
    private final LongSupplier updatePeriodTicks;
    private final Logger logger;
    private long ticksUntilNextUpdate;

    public TerritoryBoundaryProximityTask(
        SanctuaryRepository repository,
        TerritoryBoundaryService boundaryService,
        BooleanSupplier enabled,
        DoubleSupplier minimumDistance,
        DoubleSupplier maximumDistance,
        DoubleSupplier horizontalSpacing,
        DoubleSupplier verticalSpacing,
        LongSupplier updatePeriodTicks,
        Logger logger
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.boundaryService = Objects.requireNonNull(boundaryService, "boundaryService");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.minimumDistance = Objects.requireNonNull(minimumDistance, "minimumDistance");
        this.maximumDistance = Objects.requireNonNull(maximumDistance, "maximumDistance");
        this.horizontalSpacing = Objects.requireNonNull(horizontalSpacing, "horizontalSpacing");
        this.verticalSpacing = Objects.requireNonNull(verticalSpacing, "verticalSpacing");
        this.updatePeriodTicks = Objects.requireNonNull(updatePeriodTicks, "updatePeriodTicks");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public BukkitTask start(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
    }

    @Override
    public void run() {
        if (ticksUntilNextUpdate > 0L) {
            ticksUntilNextUpdate--;
            return;
        }

        long updatePeriod = updatePeriodTicks.getAsLong();
        ticksUntilNextUpdate = Math.max(1L, updatePeriod) - 1L;

        if (!enabled.getAsBoolean()) {
            return;
        }
        double minimum = minimumDistance.getAsDouble();
        double maximum = maximumDistance.getAsDouble();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (Sanctuary sanctuary : repository.findActiveInWorld(player.getWorld().getName())) {
                    if (!boundaryService.isWithinRenderDistance(
                        sanctuary,
                        player.getWorld().getName(),
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ(),
                        maximum
                    )) {
                        continue;
                    }
                    boundaryService.showProximity(
                        player,
                        sanctuary,
                        horizontalSpacing.getAsDouble(),
                        verticalSpacing.getAsDouble(),
                        minimum,
                        maximum
                    );
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to render nearby Sanctuary boundaries", exception);
        }
    }
}

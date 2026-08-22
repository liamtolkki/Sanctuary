package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
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
    private final DoubleSupplier triggerDistance;
    private final DoubleSupplier horizontalSpacing;
    private final DoubleSupplier verticalSpacing;
    private final Logger logger;

    public TerritoryBoundaryProximityTask(
        SanctuaryRepository repository,
        TerritoryBoundaryService boundaryService,
        BooleanSupplier enabled,
        DoubleSupplier triggerDistance,
        DoubleSupplier horizontalSpacing,
        DoubleSupplier verticalSpacing,
        Logger logger
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.boundaryService = Objects.requireNonNull(boundaryService, "boundaryService");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.triggerDistance = Objects.requireNonNull(triggerDistance, "triggerDistance");
        this.horizontalSpacing = Objects.requireNonNull(horizontalSpacing, "horizontalSpacing");
        this.verticalSpacing = Objects.requireNonNull(verticalSpacing, "verticalSpacing");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public BukkitTask start(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 10L);
    }

    @Override
    public void run() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        double trigger = triggerDistance.getAsDouble();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (Sanctuary sanctuary : repository.findActiveInWorld(player.getWorld().getName())) {
                    if (sanctuary.position().isEmpty()) {
                        continue;
                    }
                    double distance = TerritoryCalculator.distanceToBoundary(
                        sanctuary.position().orElseThrow(),
                        sanctuary.territoryRadius(),
                        player.getLocation().getX(),
                        player.getLocation().getZ()
                    );
                    if (distance <= trigger) {
                        boundaryService.showProximity(
                            player,
                            sanctuary,
                            horizontalSpacing.getAsDouble(),
                            verticalSpacing.getAsDouble(),
                            trigger
                        );
                    }
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to render nearby Sanctuary boundaries", exception);
        }
    }
}

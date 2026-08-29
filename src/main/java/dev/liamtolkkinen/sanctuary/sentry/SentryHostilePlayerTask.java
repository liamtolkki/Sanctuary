package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.security.SanctuaryThreat;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Proactively reacquires players who are already hostile to a Sanctuary. */
public final class SentryHostilePlayerTask implements Runnable {
    private final SentryService service;
    private final SentryRepository repository;
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuarySecurityService securityService;
    private final Logger logger;

    public SentryHostilePlayerTask(
        SentryService service,
        SentryRepository repository,
        SanctuaryRepository sanctuaryRepository,
        SanctuarySecurityService securityService,
        Logger logger
    ) {
        this.service = service;
        this.repository = repository;
        this.sanctuaryRepository = sanctuaryRepository;
        this.securityService = securityService;
        this.logger = logger;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 10L, 10L);
    }

    @Override
    public void run() {
        try {
            for (SentryRecord sentry : repository.findAll()) {
                if (sentry.state() != SentryState.ACTIVE || service.authorizedTarget(sentry).isPresent()) {
                    continue;
                }

                Sanctuary sanctuary = sanctuaryRepository.findById(sentry.sanctuaryId()).orElse(null);
                if (sanctuary == null || sanctuary.state() != SanctuaryState.ACTIVE) {
                    continue;
                }

                SentryDefinition definition = service.definition(sentry).orElse(null);
                Mob mob = service.entity(sentry)
                    .filter(Mob.class::isInstance)
                    .map(Mob.class::cast)
                    .filter(entity -> !entity.isDead())
                    .orElse(null);
                if (definition == null || mob == null) {
                    continue;
                }

                Location home = service.home(sentry);
                if (home.getWorld() == null) {
                    continue;
                }

                Player closest = null;
                double closestDistanceSquared = Double.POSITIVE_INFINITY;
                double radius = definition.targetRadius();
                for (Entity entity : home.getWorld().getNearbyEntities(home, radius, radius, radius)) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }
                    if (securityService.threat(sanctuary, player.getUniqueId()) != SanctuaryThreat.HOSTILE) {
                        continue;
                    }
                    if (!service.validTarget(sanctuary, sentry, definition, player)) {
                        continue;
                    }

                    double dx = player.getX() - home.getX();
                    double dz = player.getZ() - home.getZ();
                    double distanceSquared = dx * dx + dz * dz;
                    if (distanceSquared < closestDistanceSquared) {
                        closest = player;
                        closestDistanceSquared = distanceSquared;
                    }
                }

                if (closest != null) {
                    service.authorizeAndEngage(sanctuary, sentry, definition, mob, closest);
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed hostile player sentry scan", exception);
        }
    }
}

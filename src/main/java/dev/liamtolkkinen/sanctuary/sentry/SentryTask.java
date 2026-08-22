package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryTask implements Runnable {
    private static final Set<EntityType> NEUTRAL_TYPES = Set.of(
        EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.BEE,
        EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.LLAMA, EntityType.POLAR_BEAR
    );

    private final SentryService service;
    private final SentryRepository repository;
    private final SanctuaryRepository sanctuaryRepository;
    private final Logger logger;
    private final Set<String> mobPresence = new HashSet<>();

    public SentryTask(SentryService service, SentryRepository repository, SanctuaryRepository sanctuaryRepository, Logger logger) {
        this.service = service;
        this.repository = repository;
        this.sanctuaryRepository = sanctuaryRepository;
        this.logger = logger;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 10L, 10L);
    }

    @Override
    public void run() {
        try {
            Instant now = Instant.now();
            for (SentryRecord sentry : repository.findAll()) tickSentry(sentry, now);
            scanTriggers();
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry maintenance tick", exception);
        }
    }

    private void tickSentry(SentryRecord sentry, Instant now) throws SQLException {
        Sanctuary sanctuary = sanctuaryRepository.findById(sentry.sanctuaryId()).orElse(null);
        if (sanctuary == null || sanctuary.position().isEmpty()) return;
        var homeWorld = Bukkit.getWorld(sentry.world());
        if (homeWorld == null || !homeWorld.isChunkLoaded(sentry.x() >> 4, sentry.z() >> 4)) return;

        if (sentry.state() == SentryState.DOWN) {
            if (sentry.respawnAt().isPresent() && !now.isBefore(sentry.respawnAt().orElseThrow())) {
                SentryDefinition definition = service.definition(sentry).orElse(null);
                if (definition != null) service.spawn(sentry, definition, sanctuary, now);
            }
            return;
        }

        Entity entity = service.entity(sentry).orElse(null);
        if (!(entity instanceof Mob mob) || entity.isDead()) {
            if (sentry.state() != SentryState.DISABLED) service.markDown(sentry);
            return;
        }

        Location loc = entity.getLocation();
        if (!TerritoryCalculator.contains(
            sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), sentry.world(), loc.getX(), loc.getZ())) {
            service.markDown(sentry);
            return;
        }

        if (sentry.state() == SentryState.DISABLED) return;

        if (sentry.state() == SentryState.RECALLING) {
            double distance = loc.distance(service.home(sentry).clone().add(0, 1, 0));
            if (distance <= SentryService.HOME_REACHED_DISTANCE) {
                service.setDisabled(sentry, false);
                return;
            }
            if (sentry.recallDeadline().isPresent() && !now.isBefore(sentry.recallDeadline().orElseThrow())) {
                service.teleportHome(sentry);
                service.setDisabled(sentry, false);
                return;
            }
            service.moveHome(mob, sentry);
            return;
        }

        SentryDefinition definition = service.definition(sentry).orElse(null);
        LivingEntity target = service.authorizedTarget(sentry).orElse(null);
        if (target != null && definition != null && service.validTarget(sanctuary, sentry, definition, target)) {
            service.maintainAuthorizedTarget(sentry, mob, target, now);
        } else {
            if (target != null) service.clearTarget(sentry);
            service.idleAtHome(mob, sentry);
        }

        service.tickVexCompanions(sentry, mob, service.authorizedTarget(sentry).orElse(null), now);
    }

    private void scanTriggers() throws SQLException {
        Set<String> current = new HashSet<>();
        for (Sanctuary sanctuary : sanctuaryRepository.findAll()) {
            if (sanctuary.position().isEmpty()
                || sanctuary.state() != dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState.ACTIVE) continue;
            var world = Bukkit.getWorld(sanctuary.position().orElseThrow().world());
            if (world == null) continue;

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Vex vex) service.ensureVexCompanion(vex);
                if (!(entity instanceof LivingEntity living) || service.isDefenseEntity(entity)) continue;
                Location location = entity.getLocation();
                if (!TerritoryCalculator.contains(
                    sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), world.getName(),
                    location.getX(), location.getZ())) continue;

                if (entity instanceof Player player) {
                    double dx = location.getX() - (sanctuary.position().orElseThrow().x() + 0.5);
                    double dz = location.getZ() - (sanctuary.position().orElseThrow().z() + 0.5);
                    if (dx * dx + dz * dz <= SentryService.BEACON_PROXIMITY_RADIUS * SentryService.BEACON_PROXIMITY_RADIUS) {
                        service.trigger(sanctuary, SentryTrigger.BEACON_PROXIMITY, player);
                    }
                    continue;
                }

                String key = sanctuary.id() + ":" + entity.getUniqueId();
                current.add(key);
                if (NEUTRAL_TYPES.contains(entity.getType())) {
                    service.trigger(sanctuary, SentryTrigger.NEUTRAL_MOB_ENTERED, living);
                } else if (entity instanceof Enemy) {
                    service.trigger(sanctuary, SentryTrigger.HOSTILE_MOB_ENTERED, living);
                }
            }
        }
        mobPresence.clear();
        mobPresence.addAll(current);
    }
}

package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Warden;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SentryTask implements Runnable {
    private static final Set<EntityType> NEUTRAL_TYPES = Set.of(
        EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.BEE,
        EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.LLAMA, EntityType.POLAR_BEAR
    );

    private static final double CREEPER_MAX_HEALTH = 30.0;
    private static final double CREEPER_MOVEMENT_SPEED = 0.38;
    private static final double CREEPER_IGNITION_RANGE = 3.5;
    private static final int CREEPER_FUSE_TICKS = 12;
    private static final int CREEPER_EXPLOSION_RADIUS = 3;

    private static final double WARDEN_MELEE_RANGE = 3.0;
    private static final double WARDEN_SONIC_MIN_HORIZONTAL_RANGE = 15.0;
    private static final double WARDEN_SONIC_VERTICAL_RANGE = 20.0;
    private static final double WARDEN_SONIC_DAMAGE = 10.0;
    private static final double WARDEN_SONIC_HORIZONTAL_KNOCKBACK = 2.5;
    private static final double WARDEN_SONIC_VERTICAL_KNOCKBACK = 0.5;
    private static final Duration WARDEN_MELEE_COOLDOWN = Duration.ofMillis(1000);
    private static final Duration WARDEN_SONIC_CHARGE_TIME = Duration.ofMillis(1700);
    private static final Duration WARDEN_SONIC_COOLDOWN = Duration.ofSeconds(2);

    private final SentryService service;
    private final SentryRepository repository;
    private final SanctuaryRepository sanctuaryRepository;
    private final AnchorTerritoryService anchorTerritoryService;
    private final Logger logger;
    private final Set<String> playerPresence = new HashSet<>();
    private final Map<UUID, Instant> wardenLastMeleeAttack = new HashMap<>();
    private final Map<UUID, Instant> wardenSonicChargeStarted = new HashMap<>();
    private final Map<UUID, Instant> wardenSonicCooldownUntil = new HashMap<>();

    public SentryTask(
        SentryService service,
        SentryRepository repository,
        SanctuaryRepository sanctuaryRepository,
        AnchorTerritoryService anchorTerritoryService,
        Logger logger
    ) {
        this.service = service;
        this.repository = repository;
        this.sanctuaryRepository = sanctuaryRepository;
        this.anchorTerritoryService = anchorTerritoryService;
        this.logger = logger;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 10L, 10L);
    }

    @Override
    public void run() {
        try {
            Instant now = Instant.now();
            List<SentryRecord> sentries = repository.findAll();
            List<Sanctuary> sanctuaries = sanctuaryRepository.findAll();
            Map<UUID, Sanctuary> sanctuariesById = new HashMap<>();
            Map<UUID, List<SanctuaryAnchor>> activeAnchorsBySanctuary = new HashMap<>();
            for (Sanctuary sanctuary : sanctuaries) {
                sanctuariesById.put(sanctuary.id(), sanctuary);
            }
            for (SentryRecord sentry : sentries) {
                if (!activeAnchorsBySanctuary.containsKey(sentry.sanctuaryId())) {
                    activeAnchorsBySanctuary.put(
                        sentry.sanctuaryId(),
                        anchorTerritoryService.activeAnchors(sentry.sanctuaryId())
                    );
                }
            }

            scanTriggers(sentries, sanctuariesById, activeAnchorsBySanctuary);

            for (SentryRecord sentry : sentries) {
                tickSentry(
                    sentry,
                    sanctuariesById.get(sentry.sanctuaryId()),
                    activeAnchorsBySanctuary.getOrDefault(sentry.sanctuaryId(), List.of()),
                    now
                );
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry maintenance tick", exception);
        }
    }

    private void tickSentry(
        SentryRecord sentry,
        Sanctuary sanctuary,
        List<SanctuaryAnchor> activeAnchors,
        Instant now
    ) throws SQLException {
        if (sanctuary == null) {
            clearWardenCombatState(sentry.id());
            service.entity(sentry).ifPresent(Entity::remove);
            return;
        }

        if (sanctuary.state() != SanctuaryState.ACTIVE || activeAnchors.isEmpty()) {
            clearWardenCombatState(sentry.id());
            service.suspendForInactiveSanctuary(sentry);
            return;
        }

        var homeWorld = Bukkit.getWorld(sentry.world());
        if (homeWorld == null || !homeWorld.isChunkLoaded(sentry.x() >> 4, sentry.z() >> 4)) return;

        Location home = service.home(sentry);
        if (!containsTerritory(activeAnchors, sentry.world(), home.getX(), home.getZ())) {
            // Territory topology changed underneath this post. This is not combat death: keep the
            // Sanctuary-bound record suspended so it can return if another active anchor covers
            // the post again later.
            clearWardenCombatState(sentry.id());
            service.suspendForInactiveSanctuary(sentry);
            return;
        }

        if (sentry.state() == SentryState.DOWN) {
            clearWardenCombatState(sentry.id());
            service.updateCooldownVisual(sentry, now);
            if (sentry.respawnAt().isPresent() && !now.isBefore(sentry.respawnAt().orElseThrow())) {
                SentryDefinition definition = service.definition(sentry).orElse(null);
                if (definition != null) service.spawn(sentry, definition, sanctuary, now);
            }
            return;
        }

        service.clearCooldownVisual(sentry);
        Entity entity = service.entity(sentry).orElse(null);
        if (!(entity instanceof Mob mob) || entity.isDead()) {
            clearWardenCombatState(sentry.id());
            if (sentry.entityId().isEmpty()) {
                service.restoreForActiveSanctuary(sentry, sanctuary, now);
                return;
            }
            if (sentry.state() == SentryState.DISABLED) {
                service.suspendForInactiveSanctuary(sentry);
                SentryRecord suspended = repository.findById(sentry.id()).orElse(sentry);
                service.restoreForActiveSanctuary(suspended, sanctuary, now);
                return;
            }
            service.markDown(sentry);
            return;
        }

        if (mob instanceof Creeper creeper) configureCreeperBody(creeper);

        Location loc = entity.getLocation();
        if (!containsTerritory(activeAnchors, sentry.world(), loc.getX(), loc.getZ())) {
            // Leaving the entire Sanctuary is still fatal. Crossing from one anchor circle into
            // another circle belonging to this same Sanctuary is allowed.
            clearWardenCombatState(sentry.id());
            service.markDown(sentry);
            return;
        }

        if (sentry.state() == SentryState.DISABLED) {
            clearWardenCombatState(sentry.id());
            if (mob instanceof Creeper creeper) disarmCreeper(creeper);
            return;
        }

        if (sentry.state() == SentryState.RECALLING) {
            clearWardenCombatState(sentry.id());
            if (mob instanceof Creeper creeper) disarmCreeper(creeper);
            double distance = loc.distance(service.postStandLocation(sentry));
            if (distance <= SentryService.HOME_REACHED_DISTANCE) {
                service.teleportHome(sentry);
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
            if (mob instanceof Creeper creeper) {
                tickCreeperCombat(sentry, creeper, target, now);
            } else if (mob instanceof Warden warden) {
                tickWardenCombat(sentry, warden, target, now);
            } else {
                service.maintainAuthorizedTarget(sentry, mob, target, now);
            }
        } else {
            clearWardenCombatState(sentry.id());
            if (mob instanceof Creeper creeper) disarmCreeper(creeper);
            if (target != null) service.clearTarget(sentry);
            service.idleAtHome(mob, sentry);
        }

        service.tickVexCompanions(sentry, mob, service.authorizedTarget(sentry).orElse(null), now);
    }

    private void configureCreeperBody(Creeper creeper) {
        creeper.setPowered(true);
        creeper.setMaxFuseTicks(CREEPER_FUSE_TICKS);
        creeper.setExplosionRadius(CREEPER_EXPLOSION_RADIUS);

        var maxHealth = creeper.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - CREEPER_MAX_HEALTH) > 0.001) {
            double previousMaximum = maxHealth.getValue();
            double previousHealth = creeper.getHealth();
            maxHealth.setBaseValue(CREEPER_MAX_HEALTH);
            if (previousHealth >= previousMaximum - 0.001) {
                creeper.setHealth(CREEPER_MAX_HEALTH);
            }
        }

        var movementSpeed = creeper.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null && Math.abs(movementSpeed.getBaseValue() - CREEPER_MOVEMENT_SPEED) > 0.001) {
            movementSpeed.setBaseValue(CREEPER_MOVEMENT_SPEED);
        }
    }

    private void tickCreeperCombat(SentryRecord sentry, Creeper creeper, LivingEntity target, Instant now) {
        configureCreeperBody(creeper);
        service.maintainAuthorizedTarget(sentry, creeper, target, now);

        if (creeper.isIgnited()) return;

        double distanceSquared = creeper.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared <= CREEPER_IGNITION_RANGE * CREEPER_IGNITION_RANGE) {
            creeper.setIgnited(true);
        }
    }

    private void disarmCreeper(Creeper creeper) {
        creeper.setIgnited(false);
        creeper.setFuseTicks(0);
    }

    private void tickWardenCombat(SentryRecord sentry, Warden warden, LivingEntity target, Instant now) {
        warden.setAware(true);
        warden.setAggressive(true);

        warden.setTarget(null);
        LivingEntity angryAt = warden.getEntityAngryAt();
        if (angryAt != null) warden.clearAnger(angryAt);

        double horizontalDx = target.getX() - warden.getX();
        double horizontalDz = target.getZ() - warden.getZ();
        double horizontalDistanceSquared = horizontalDx * horizontalDx + horizontalDz * horizontalDz;
        double verticalDistance = Math.abs(target.getY() - warden.getY());
        double distanceSquared = warden.getLocation().distanceSquared(target.getLocation());
        double sonicMinimumSquared = WARDEN_SONIC_MIN_HORIZONTAL_RANGE * WARDEN_SONIC_MIN_HORIZONTAL_RANGE;

        Instant chargeStarted = wardenSonicChargeStarted.get(sentry.id());
        if (chargeStarted != null) {
            boolean stillInSonicRange = horizontalDistanceSquared > sonicMinimumSquared
                && verticalDistance <= WARDEN_SONIC_VERTICAL_RANGE;
            if (!stillInSonicRange) {
                wardenSonicChargeStarted.remove(sentry.id());
            } else {
                warden.getPathfinder().stopPathfinding();
                warden.lookAt(target);
                if (!now.isBefore(chargeStarted.plus(WARDEN_SONIC_CHARGE_TIME))) {
                    fireWardenSonicBoom(warden, target);
                    wardenSonicChargeStarted.remove(sentry.id());
                    wardenSonicCooldownUntil.put(sentry.id(), now.plus(WARDEN_SONIC_COOLDOWN));
                }
                return;
            }
        }

        if (distanceSquared <= WARDEN_MELEE_RANGE * WARDEN_MELEE_RANGE) {
            warden.getPathfinder().stopPathfinding();
            warden.lookAt(target);
            Instant previousAttack = wardenLastMeleeAttack.get(sentry.id());
            if (previousAttack == null || !now.isBefore(previousAttack.plus(WARDEN_MELEE_COOLDOWN))) {
                warden.attack(target);
                wardenLastMeleeAttack.put(sentry.id(), now);
            }
            return;
        }

        if (horizontalDistanceSquared <= sonicMinimumSquared) {
            var path = warden.getPathfinder().findPath(target);
            if (path != null) warden.getPathfinder().moveTo(path, 1.2);
            return;
        }

        Instant sonicCooldown = wardenSonicCooldownUntil.get(sentry.id());
        boolean sonicReady = sonicCooldown == null || !now.isBefore(sonicCooldown);
        boolean inSonicRange = verticalDistance <= WARDEN_SONIC_VERTICAL_RANGE;
        if (sonicReady && inSonicRange) {
            warden.getPathfinder().stopPathfinding();
            warden.lookAt(target);
            warden.getWorld().playSound(warden.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3.0f, 1.0f);
            wardenSonicChargeStarted.put(sentry.id(), now);
            return;
        }

        var path = warden.getPathfinder().findPath(target);
        if (path != null) warden.getPathfinder().moveTo(path, 1.2);
    }

    private void fireWardenSonicBoom(Warden warden, LivingEntity target) {
        Location origin = warden.getEyeLocation();
        Location destination = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector delta = destination.toVector().subtract(origin.toVector());
        double distance = delta.length();
        if (distance <= 0.001) return;

        Vector direction = delta.clone().normalize();
        for (double travelled = 1.0; travelled < distance; travelled += 1.5) {
            Location particleLocation = origin.clone().add(direction.clone().multiply(travelled));
            warden.getWorld().spawnParticle(Particle.SONIC_BOOM, particleLocation, 1, 0, 0, 0, 0);
        }
        warden.getWorld().playSound(warden.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);

        DamageSource damageSource = DamageSource.builder(DamageType.SONIC_BOOM)
            .withCausingEntity(warden)
            .withDirectEntity(warden)
            .withDamageLocation(origin)
            .build();
        target.damage(WARDEN_SONIC_DAMAGE, damageSource);

        Vector knockback = target.getLocation().toVector().subtract(warden.getLocation().toVector());
        knockback.setY(0);
        if (knockback.lengthSquared() > 0.0001) {
            knockback.normalize().multiply(WARDEN_SONIC_HORIZONTAL_KNOCKBACK);
        }
        knockback.setY(WARDEN_SONIC_VERTICAL_KNOCKBACK);
        target.setVelocity(target.getVelocity().add(knockback));
    }

    private void clearWardenCombatState(UUID sentryId) {
        wardenLastMeleeAttack.remove(sentryId);
        wardenSonicChargeStarted.remove(sentryId);
        wardenSonicCooldownUntil.remove(sentryId);
    }

    private void scanTriggers(
        List<SentryRecord> sentries,
        Map<UUID, Sanctuary> sanctuariesById,
        Map<UUID, List<SanctuaryAnchor>> activeAnchorsBySanctuary
    ) throws SQLException {
        Set<String> currentPlayers = new HashSet<>();

        for (SentryRecord sentry : sentries) {
            if (sentry.state() != SentryState.ACTIVE) continue;

            Sanctuary sanctuary = sanctuariesById.get(sentry.sanctuaryId());
            if (sanctuary == null || sanctuary.state() != SanctuaryState.ACTIVE) continue;

            List<SanctuaryAnchor> activeAnchors = activeAnchorsBySanctuary.getOrDefault(
                sentry.sanctuaryId(),
                List.of()
            );
            if (activeAnchors.isEmpty()) continue;

            SentryDefinition definition = service.definition(sentry).orElse(null);
            Mob mob = service.entity(sentry)
                .filter(Mob.class::isInstance)
                .map(Mob.class::cast)
                .filter(entity -> !entity.isDead())
                .orElse(null);
            if (definition == null || mob == null) continue;

            var world = Bukkit.getWorld(sentry.world());
            if (world == null) continue;
            Location home = service.home(sentry);
            if (!containsTerritory(activeAnchors, sentry.world(), home.getX(), home.getZ())) continue;

            double targetRadius = definition.targetRadius();
            List<Player> players = new ArrayList<>();
            List<LivingEntity> hostileMobs = new ArrayList<>();
            List<LivingEntity> neutralMobs = new ArrayList<>();

            // Deliberately scan only around this sentry's post. Chaining anchors can make a
            // Sanctuary arbitrarily large; Sanctuary size must never expand a sentry's watch cost.
            for (Entity entity : world.getNearbyEntities(
                home,
                targetRadius,
                targetRadius,
                targetRadius
            )) {
                if (entity instanceof Vex vex && !service.isCompanion(vex)) {
                    service.ensureVexCompanion(vex);
                }
                if (!(entity instanceof LivingEntity living)
                    || service.isDefenseEntity(entity)
                    || isPlayerCompanion(entity)) continue;

                Location location = entity.getLocation();
                if (horizontalDistanceSquared(home, location) > targetRadius * targetRadius) continue;
                if (!containsTerritory(
                    activeAnchors,
                    world.getName(),
                    location.getX(),
                    location.getZ()
                )) continue;

                if (entity instanceof Player player) {
                    players.add(player);
                } else if (NEUTRAL_TYPES.contains(entity.getType())) {
                    neutralMobs.add(living);
                } else if (isHostileMob(entity)) {
                    hostileMobs.add(living);
                }
            }

            if (!players.isEmpty()) {
                boolean unauthorizedEntryEnabled = service.effective(
                    sentry,
                    SentryTrigger.UNAUTHORIZED_PLAYER_ENTERED
                );
                boolean beaconProximityEnabled = service.effective(
                    sentry,
                    SentryTrigger.BEACON_PROXIMITY
                );
                for (Player player : players) {
                    String key = sentry.id() + ":" + player.getUniqueId();
                    currentPlayers.add(key);
                    boolean validTarget = service.validTarget(sanctuary, sentry, definition, player);
                    if (!validTarget) continue;

                    if (unauthorizedEntryEnabled && !playerPresence.contains(key)) {
                        service.authorizeAndEngage(sanctuary, sentry, definition, mob, player);
                    }
                    if (beaconProximityEnabled
                        && isNearActiveAnchor(activeAnchors, player.getLocation(), SentryService.BEACON_PROXIMITY_RADIUS)) {
                        service.authorizeAndEngage(sanctuary, sentry, definition, mob, player);
                    }
                }
            }

            LivingEntity currentTarget = service.authorizedTarget(sentry).orElse(null);
            if (currentTarget != null && service.validTarget(sanctuary, sentry, definition, currentTarget)) {
                continue;
            }

            boolean hostileEnabled = !hostileMobs.isEmpty()
                && service.effective(sentry, SentryTrigger.HOSTILE_MOB_ENTERED);
            boolean neutralEnabled = !neutralMobs.isEmpty()
                && service.effective(sentry, SentryTrigger.NEUTRAL_MOB_ENTERED);
            if (!hostileEnabled && !neutralEnabled) continue;

            LivingEntity target = null;
            double bestDistanceSquared = Double.POSITIVE_INFINITY;

            if (hostileEnabled) {
                for (LivingEntity candidate : hostileMobs) {
                    double distanceSquared = horizontalDistanceSquared(home, candidate.getLocation());
                    if (distanceSquared < bestDistanceSquared) {
                        target = candidate;
                        bestDistanceSquared = distanceSquared;
                    }
                }
            }
            if (neutralEnabled) {
                for (LivingEntity candidate : neutralMobs) {
                    double distanceSquared = horizontalDistanceSquared(home, candidate.getLocation());
                    if (distanceSquared < bestDistanceSquared) {
                        target = candidate;
                        bestDistanceSquared = distanceSquared;
                    }
                }
            }

            if (target != null) {
                service.authorizeAndEngage(sanctuary, sentry, definition, mob, target);
            }
        }

        playerPresence.clear();
        playerPresence.addAll(currentPlayers);
    }

    private static boolean containsTerritory(
        List<SanctuaryAnchor> activeAnchors,
        String world,
        double x,
        double z
    ) {
        for (SanctuaryAnchor anchor : activeAnchors) {
            if (anchor.position().isEmpty()) continue;
            if (TerritoryCalculator.contains(
                anchor.position().orElseThrow(),
                anchor.territoryRadius(),
                world,
                x,
                z
            )) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearActiveAnchor(
        List<SanctuaryAnchor> activeAnchors,
        Location location,
        double radius
    ) {
        double radiusSquared = radius * radius;
        for (SanctuaryAnchor anchor : activeAnchors) {
            if (anchor.position().isEmpty()) continue;
            var position = anchor.position().orElseThrow();
            if (!position.world().equals(location.getWorld().getName())) continue;
            double dx = location.getX() - (position.x() + 0.5);
            double dz = location.getZ() - (position.z() + 0.5);
            if (dx * dx + dz * dz <= radiusSquared) return true;
        }
        return false;
    }

    private static boolean isHostileMob(Entity entity) {
        return entity instanceof Enemy
            || entity.getType() == EntityType.SLIME
            || entity.getType() == EntityType.MAGMA_CUBE;
    }

    private static double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isPlayerCompanion(Entity entity) {
        return entity.getPersistentDataContainer().getKeys().stream().anyMatch(key ->
            key.getNamespace().equals("sanctuary")
                && (key.getKey().equals("companion_id")
                    || key.getKey().equals("companion_vex_parent"))
        );
    }
}

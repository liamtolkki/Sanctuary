package dev.liamtolkkinen.sanctuary.sentry;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.security.SanctuaryRelationship;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryService {
    public static final Duration RESPAWN_COOLDOWN = Duration.ofSeconds(30);
    public static final Duration RECALL_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration VEX_IDLE_TIMEOUT = Duration.ofSeconds(30);
    public static final double HOME_REACHED_DISTANCE = 1.75;
    public static final double BEACON_PROXIMITY_RADIUS = 12.0;

    private final JavaPlugin plugin;
    private final SanctuaryRepository sanctuaryRepository;
    private final SentryRepository repository;
    private final SanctuarySecurityService securityService;
    private final TerritoryPresenceService presenceService;
    private final Logger logger;
    private final NamespacedKey sentryIdKey;
    private final NamespacedKey vexParentKey;
    private final Map<UUID, UUID> authorizedTargets = new HashMap<>();
    private final Map<UUID, UUID> vexParents = new HashMap<>();
    private final Map<UUID, Instant> vexActivity = new HashMap<>();
    private final Set<UUID> authorizedTeleports = new HashSet<>();

    public SentryService(
        JavaPlugin plugin,
        SanctuaryRepository sanctuaryRepository,
        SentryRepository repository,
        SanctuarySecurityService securityService,
        TerritoryPresenceService presenceService,
        Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sanctuaryRepository = Objects.requireNonNull(sanctuaryRepository, "sanctuaryRepository");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.securityService = Objects.requireNonNull(securityService, "securityService");
        this.presenceService = Objects.requireNonNull(presenceService, "presenceService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.sentryIdKey = new NamespacedKey(plugin, "sentry_id");
        this.vexParentKey = new NamespacedKey(plugin, "sentry_vex_parent");
    }

    public Optional<SentryDefinition> definition(org.bukkit.inventory.ItemStack item) {
        return ExtendedItems.getId(item)
            .flatMap(id -> SentryDefinition.ALL.stream().filter(d -> d.itemId().equals(id)).findFirst());
    }

    public Optional<Sanctuary> sanctuaryAt(Location location) throws SQLException {
        List<Sanctuary> candidates = sanctuaryRepository.findActiveInWorld(location.getWorld().getName());
        return presenceService.findCurrentSanctuary(candidates, location.getWorld().getName(), location.getX(), location.getZ());
    }

    public SentryRecord register(Sanctuary sanctuary, SentryDefinition definition, Location post) throws SQLException {
        Instant now = Instant.now();
        SentryRecord record = new SentryRecord(
            UUID.randomUUID(), sanctuary.id(), definition.persistentId(), post.getWorld().getName(),
            post.getBlockX(), post.getBlockY(), post.getBlockZ(), Optional.empty(), SentryState.ACTIVE,
            Optional.empty(), Optional.empty(), now, now
        );
        repository.save(record);
        return spawn(record, definition, sanctuary, now);
    }

    public SentryRecord spawn(SentryRecord record, SentryDefinition definition, Sanctuary sanctuary, Instant now) throws SQLException {
        World world = Bukkit.getWorld(record.world());
        if (world == null || sanctuary.position().isEmpty()) return record;
        Location home = home(record);
        if (!TerritoryCalculator.contains(sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), record.world(), home.getX(), home.getZ())) return record;
        if (!world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) return record;
        if (world.getBlockAt(record.x(), record.y(), record.z()).getType() != ExtendedItems.create(definition.itemId()).getType()) return record;

        Entity spawned = world.spawnEntity(home.clone().add(0, 1.0, 0), definition.entityType());
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            throw new IllegalStateException("Sentry type is not a Mob: " + definition.entityType());
        }

        mob.setPersistent(true);
        mob.getPersistentDataContainer().set(sentryIdKey, PersistentDataType.STRING, record.id().toString());
        mob.customName(net.kyori.adventure.text.Component.text(definition.displayName()));
        mob.setCustomNameVisible(false);
        if (definition.baby() && mob instanceof Zombie zombie) zombie.setBaby();
        configureManagedAi(mob);

        SentryRecord active = new SentryRecord(
            record.id(), record.sanctuaryId(), record.itemId(), record.world(), record.x(), record.y(), record.z(),
            Optional.of(mob.getUniqueId()), SentryState.ACTIVE, Optional.empty(), Optional.empty(), record.createdAt(), now
        );
        repository.save(active);
        return active;
    }

    /** Sanctuary owns target selection. Native attack and movement goals remain available. */
    public void configureManagedAi(Mob mob) {
        Bukkit.getMobGoals().removeAllGoals(mob, GoalType.TARGET);
        mob.setAware(true);
        mob.setTarget(null);
        mob.setAggressive(false);
        clearWitherTargets(mob);
        if (mob instanceof org.bukkit.entity.Slime slime) slime.setWander(false);
    }

    public void unregister(SentryRecord record) throws SQLException {
        removeVexCompanions(record);
        entity(record).ifPresent(Entity::remove);
        authorizedTargets.remove(record.id());
        repository.clearOverrides(record.id());
        repository.delete(record.id());
    }

    public void markDown(SentryRecord record) throws SQLException {
        removeVexCompanions(record);
        entity(record).ifPresent(Entity::remove);
        authorizedTargets.remove(record.id());
        Instant now = Instant.now();
        repository.save(new SentryRecord(record.id(), record.sanctuaryId(), record.itemId(), record.world(), record.x(), record.y(), record.z(),
            Optional.empty(), SentryState.DOWN, Optional.of(now.plus(RESPAWN_COOLDOWN)), Optional.empty(), record.createdAt(), now));
    }

    public void setDisabled(SentryRecord record, boolean disabled) throws SQLException {
        Instant now = Instant.now();
        SentryState state = disabled ? SentryState.DISABLED : SentryState.ACTIVE;
        Optional<UUID> entityId = record.entityId();
        if (disabled) removeVexCompanions(record);
        entity(record).ifPresent(entity -> {
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                mob.setAware(!disabled);
                mob.setAggressive(false);
                clearWitherTargets(mob);
                mob.customName(net.kyori.adventure.text.Component.text(
                    definition(record).map(SentryDefinition::displayName).orElse("Sentry") + (disabled ? " [Disabled]" : "")
                ));
                mob.setCustomNameVisible(disabled);
                if (!disabled) {
                    configureManagedAi(mob);
                    moveHome(mob, record);
                }
            }
        });
        authorizedTargets.remove(record.id());
        repository.save(new SentryRecord(record.id(), record.sanctuaryId(), record.itemId(), record.world(), record.x(), record.y(), record.z(), entityId,
            state, Optional.empty(), Optional.empty(), record.createdAt(), now));
    }

    public void recall(SentryRecord record) throws SQLException {
        if (record.state() == SentryState.DOWN) return;
        Instant now = Instant.now();
        removeVexCompanions(record);
        authorizedTargets.remove(record.id());
        entity(record).ifPresent(entity -> {
            if (entity instanceof Mob mob) {
                mob.setAware(true);
                mob.setTarget(null);
                mob.setAggressive(false);
                clearWitherTargets(mob);
                moveHome(mob, record);
            }
        });
        repository.save(new SentryRecord(record.id(), record.sanctuaryId(), record.itemId(), record.world(), record.x(), record.y(), record.z(), record.entityId(),
            SentryState.RECALLING, Optional.empty(), Optional.of(now.plus(RECALL_TIMEOUT)), record.createdAt(), now));
    }

    public boolean effective(SentryRecord sentry, SentryTrigger trigger) throws SQLException {
        SentryOverride override = repository.getOverride(sentry.id(), trigger);
        return switch (override) {
            case ENABLED -> true;
            case DISABLED -> false;
            case INHERIT -> repository.getDefault(sentry.sanctuaryId(), trigger);
        };
    }

    public void trigger(Sanctuary sanctuary, SentryTrigger trigger, LivingEntity target) throws SQLException {
        if (target == null || target.isDead() || isDefenseEntity(target)) return;
        if (target instanceof Player player) {
            SanctuaryRelationship relationship = securityService.relationship(sanctuary, player.getUniqueId());
            if (relationship == SanctuaryRelationship.OWNER || relationship == SanctuaryRelationship.TRUSTED) return;
        }
        for (SentryRecord sentry : repository.findBySanctuary(sanctuary.id())) {
            if (sentry.state() != SentryState.ACTIVE || !effective(sentry, trigger)) continue;
            SentryDefinition definition = definition(sentry).orElse(null);
            Mob mob = entity(sentry).filter(Mob.class::isInstance).map(Mob.class::cast).orElse(null);
            if (definition == null || mob == null || !validTarget(sanctuary, sentry, definition, target)) continue;
            authorizeAndEngage(sanctuary, sentry, definition, mob, target);
        }
    }

    public void authorizeAndEngage(Sanctuary sanctuary, SentryRecord sentry, SentryDefinition definition, Mob mob, LivingEntity target) {
        if (!validTarget(sanctuary, sentry, definition, target)) {
            clearTarget(sentry);
            return;
        }
        authorizedTargets.put(sentry.id(), target.getUniqueId());
        applyAuthorizedTarget(sentry, mob, target, Instant.now());
    }

    public void maintainAuthorizedTarget(SentryRecord sentry, Mob mob, LivingEntity target, Instant now) {
        applyAuthorizedTarget(sentry, mob, target, now);
    }

    private void applyAuthorizedTarget(SentryRecord sentry, Mob mob, LivingEntity target, Instant now) {
        mob.setAware(true);
        mob.setAggressive(true);
        if (mob.getTarget() == null || !mob.getTarget().getUniqueId().equals(target.getUniqueId())) {
            mob.setTarget(target);
        }
        if (mob instanceof Wither wither) {
            for (Wither.Head head : Wither.Head.values()) wither.setTarget(head, target);
        }
        if (mob instanceof Warden warden) {
            LivingEntity angryAt = warden.getEntityAngryAt();
            if (angryAt != null && !angryAt.getUniqueId().equals(target.getUniqueId())) warden.clearAnger(angryAt);
            warden.setAnger(target, 150);
        }
        if (mob instanceof Evoker) syncVexCompanions(sentry, mob, target, now);
    }

    public Optional<LivingEntity> authorizedTarget(SentryRecord record) {
        UUID targetId = authorizedTargets.get(record.id());
        if (targetId == null) return Optional.empty();
        Entity entity = Bukkit.getEntity(targetId);
        return entity instanceof LivingEntity living && !living.isDead() ? Optional.of(living) : Optional.empty();
    }

    public boolean isManaged(Entity entity) {
        return sentryId(entity).isPresent();
    }

    public boolean isCompanion(Entity entity) {
        return vexParentId(entity).isPresent();
    }

    public boolean isDefenseEntity(Entity entity) {
        return isManaged(entity) || isCompanion(entity);
    }

    public Optional<SentryRecord> record(Entity entity) throws SQLException {
        Optional<UUID> id = sentryId(entity);
        return id.isPresent() ? repository.findById(id.orElseThrow()) : repository.findByEntity(entity.getUniqueId());
    }

    public Optional<SentryRecord> companionParent(Entity entity) throws SQLException {
        Optional<UUID> id = vexParentId(entity);
        return id.isPresent() ? repository.findById(id.orElseThrow()) : Optional.empty();
    }

    public boolean ensureVexCompanion(Entity entity) throws SQLException {
        if (!(entity instanceof Vex vex)) return false;
        Optional<UUID> existingId = vexParentId(vex);
        if (existingId.isPresent()) {
            SentryRecord parent = repository.findById(existingId.orElseThrow()).orElse(null);
            if (parent == null || !isEvoker(parent)) {
                vex.getPersistentDataContainer().remove(vexParentKey);
                vexParents.remove(vex.getUniqueId());
                return false;
            }
            vexParents.put(vex.getUniqueId(), parent.id());
            configureVex(vex, parent);
            return true;
        }

        Mob summoner = vex.getSummoner();
        if (summoner == null) return false;
        SentryRecord parent = record(summoner).orElse(null);
        if (parent == null || !isEvoker(parent)) return false;

        vex.getPersistentDataContainer().set(vexParentKey, PersistentDataType.STRING, parent.id().toString());
        vexParents.put(vex.getUniqueId(), parent.id());
        vexActivity.putIfAbsent(parent.id(), Instant.now());
        configureVex(vex, parent);
        return true;
    }

    private void configureVex(Vex vex, SentryRecord parent) {
        vex.setPersistent(true);
        Bukkit.getMobGoals().removeAllGoals(vex, GoalType.TARGET);
        vex.setBound(home(parent).clone().add(0, 1.0, 0));
        vex.setLimitedLifetime(false);
    }

    public void syncVexCompanions(SentryRecord parent, Mob evoker, LivingEntity target, Instant now) {
        if (!isEvoker(parent)) return;
        for (Vex vex : evoker.getWorld().getEntitiesByClass(Vex.class)) {
            if (vex.getSummoner() == evoker) {
                try {
                    ensureVexCompanion(vex);
                } catch (SQLException exception) {
                    logger.warning("Failed to register Evoker Vex companion: " + exception.getMessage());
                }
            }
        }

        if (target != null && !target.isDead()) vexActivity.put(parent.id(), now);
        for (Vex vex : vexCompanions(parent)) {
            if (target == null || target.isDead()) {
                vex.setTarget(null);
                vex.setAggressive(false);
                vex.setCharging(false);
            } else {
                vex.setAware(true);
                vex.setAggressive(true);
                vex.setTarget(target);
            }
        }
    }

    public void tickVexCompanions(SentryRecord parent, Mob evoker, LivingEntity target, Instant now) {
        if (!isEvoker(parent)) return;
        syncVexCompanions(parent, evoker, target, now);
        if (target != null && !target.isDead()) return;
        Instant lastActivity = vexActivity.computeIfAbsent(parent.id(), ignored -> now);
        if (!now.isBefore(lastActivity.plus(VEX_IDLE_TIMEOUT))) removeVexCompanions(parent);
    }

    public boolean targetAllowed(Mob mob, LivingEntity target) throws SQLException {
        SentryRecord record = record(mob).orElse(null);
        if (record != null) {
            return target != null && target.getUniqueId().equals(authorizedTargets.get(record.id()));
        }
        SentryRecord parent = companionParent(mob).orElse(null);
        return parent == null || (target != null && target.getUniqueId().equals(authorizedTargets.get(parent.id())));
    }

    public boolean mayDamage(Entity attacker, LivingEntity victim) throws SQLException {
        Optional<SentryRecord> record = record(attacker);
        if (record.isPresent()) {
            UUID authorized = authorizedTargets.get(record.orElseThrow().id());
            return authorized != null && authorized.equals(victim.getUniqueId());
        }
        Optional<SentryRecord> parent = companionParent(attacker);
        if (parent.isPresent()) {
            UUID authorized = authorizedTargets.get(parent.orElseThrow().id());
            return authorized != null && authorized.equals(victim.getUniqueId());
        }
        return true;
    }

    public void clearTarget(SentryRecord sentry) {
        authorizedTargets.remove(sentry.id());
        entity(sentry).filter(Mob.class::isInstance).map(Mob.class::cast).ifPresent(mob -> {
            mob.setTarget(null);
            mob.setAggressive(false);
            clearWitherTargets(mob);
            if (mob instanceof Warden warden && warden.getEntityAngryAt() != null) {
                warden.clearAnger(warden.getEntityAngryAt());
            }
            if (mob instanceof Evoker) syncVexCompanions(sentry, mob, null, Instant.now());
            moveHome(mob, sentry);
        });
    }

    public void idleAtHome(Mob mob, SentryRecord record) {
        Location home = home(record).clone().add(0, 1.0, 0);
        if (home.getWorld() == null || mob.getWorld() != home.getWorld()) return;
        if (mob.getLocation().distanceSquared(home) <= HOME_REACHED_DISTANCE * HOME_REACHED_DISTANCE) {
            mob.setTarget(null);
            mob.setAggressive(false);
            clearWitherTargets(mob);
            mob.getPathfinder().stopPathfinding();
            return;
        }
        moveHome(mob, record);
    }

    public Optional<SentryDefinition> definition(SentryRecord record) {
        return SentryDefinition.byPersistentId(record.itemId());
    }

    public Location home(SentryRecord record) {
        World world = Bukkit.getWorld(record.world());
        return world == null ? new Location(null, record.x() + 0.5, record.y(), record.z() + 0.5)
            : new Location(world, record.x() + 0.5, record.y(), record.z() + 0.5);
    }

    public Optional<Entity> entity(SentryRecord record) {
        return record.entityId().map(Bukkit::getEntity).filter(Objects::nonNull);
    }

    public void moveHome(Mob mob, SentryRecord record) {
        Location home = home(record).clone().add(0, 1.0, 0);
        if (home.getWorld() == null) return;
        try {
            Sanctuary sanctuary = sanctuaryRepository.findById(record.sanctuaryId()).orElse(null);
            if (sanctuary == null || sanctuary.position().isEmpty()) return;
            var path = mob.getPathfinder().findPath(home);
            if (path == null) return;
            boolean exits = path.getPoints().stream().anyMatch(point ->
                !TerritoryCalculator.contains(sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), record.world(), point.getX(), point.getZ()));
            if (!exits) mob.getPathfinder().moveTo(path, 1.0);
        } catch (SQLException exception) {
            logger.warning("Failed to validate sentry home path: " + exception.getMessage());
        }
    }

    public void teleportHome(SentryRecord record) {
        entity(record).ifPresent(entity -> {
            UUID id = entity.getUniqueId();
            authorizedTeleports.add(id);
            try {
                entity.teleport(home(record).clone().add(0, 1.0, 0));
            } finally {
                authorizedTeleports.remove(id);
            }
        });
    }

    public boolean isAuthorizedTeleport(Entity entity) {
        return authorizedTeleports.contains(entity.getUniqueId());
    }

    public boolean teleportDestinationAllowed(Entity entity, Location destination) throws SQLException {
        if (isAuthorizedTeleport(entity)) return true;
        SentryRecord record = record(entity).orElse(null);
        if (record == null || entity.getType() != EntityType.ENDERMAN || authorizedTarget(record).isEmpty()) return false;
        return locationAllowedForCombat(record, destination);
    }

    public boolean pathDestinationAllowed(Entity entity, Location destination) throws SQLException {
        SentryRecord record = record(entity).orElse(null);
        if (record == null) {
            SentryRecord parent = companionParent(entity).orElse(null);
            if (parent == null) return true;
            return locationAllowedForCombat(parent, destination);
        }

        if (authorizedTarget(record).isPresent()) return locationAllowedForCombat(record, destination);
        Location home = home(record).clone().add(0, 1.0, 0);
        return home.getWorld() == destination.getWorld() && home.distanceSquared(destination) <= 4.0;
    }

    private boolean locationAllowedForCombat(SentryRecord record, Location destination) throws SQLException {
        Sanctuary sanctuary = sanctuaryRepository.findById(record.sanctuaryId()).orElse(null);
        SentryDefinition definition = definition(record).orElse(null);
        if (sanctuary == null || sanctuary.position().isEmpty() || definition == null) return false;
        if (destination.getWorld() == null || !destination.getWorld().getName().equals(record.world())) return false;
        if (!TerritoryCalculator.contains(sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), record.world(), destination.getX(), destination.getZ())) return false;
        Location home = home(record);
        double dx = destination.getX() - home.getX();
        double dz = destination.getZ() - home.getZ();
        return dx * dx + dz * dz <= definition.targetRadius() * definition.targetRadius();
    }

    public boolean canManage(Player player, Sanctuary sanctuary) {
        return sanctuary.ownerId().equals(player.getUniqueId())
            || (sanctuary.debugEphemeral() && player.hasPermission("sanctuary.admin"));
    }

    public boolean validTarget(Sanctuary sanctuary, SentryRecord sentry, SentryDefinition definition, LivingEntity target) {
        if (target.getWorld() != Bukkit.getWorld(sentry.world()) || sanctuary.position().isEmpty() || isDefenseEntity(target)) return false;
        if (target instanceof Player player) {
            try {
                SanctuaryRelationship relationship = securityService.relationship(sanctuary, player.getUniqueId());
                if (relationship == SanctuaryRelationship.OWNER || relationship == SanctuaryRelationship.TRUSTED) return false;
            } catch (SQLException exception) {
                logger.warning("Failed sentry relationship check: " + exception.getMessage());
                return false;
            }
        }
        Location t = target.getLocation();
        if (!TerritoryCalculator.contains(sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), sentry.world(), t.getX(), t.getZ())) return false;
        Location h = home(sentry);
        double dx = t.getX() - h.getX();
        double dz = t.getZ() - h.getZ();
        return dx * dx + dz * dz <= definition.targetRadius() * definition.targetRadius();
    }

    public boolean shouldSuppressManagedWardenEffect(Player player) throws SQLException {
        Sanctuary sanctuary = sanctuaryAt(player.getLocation()).orElse(null);
        if (sanctuary == null) return false;
        SanctuaryRelationship relationship = securityService.relationship(sanctuary, player.getUniqueId());
        if (relationship != SanctuaryRelationship.OWNER && relationship != SanctuaryRelationship.TRUSTED) return false;
        for (SentryRecord sentry : repository.findBySanctuary(sanctuary.id())) {
            if (sentry.state() != SentryState.ACTIVE) continue;
            SentryDefinition definition = definition(sentry).orElse(null);
            if (definition != null && definition.entityType() == EntityType.WARDEN && entity(sentry).isPresent()) return true;
        }
        return false;
    }

    private boolean isEvoker(SentryRecord record) {
        SentryDefinition definition = definition(record).orElse(null);
        return definition != null && definition.entityType() == EntityType.EVOKER;
    }

    private List<Vex> vexCompanions(SentryRecord parent) {
        return vexParents.entrySet().stream()
            .filter(entry -> entry.getValue().equals(parent.id()))
            .map(entry -> Bukkit.getEntity(entry.getKey()))
            .filter(Vex.class::isInstance)
            .map(Vex.class::cast)
            .filter(vex -> !vex.isDead())
            .toList();
    }

    private void removeVexCompanions(SentryRecord parent) {
        for (Vex vex : vexCompanions(parent)) vex.remove();
        vexParents.entrySet().removeIf(entry -> entry.getValue().equals(parent.id()));
        vexActivity.remove(parent.id());
    }

    private void clearWitherTargets(Mob mob) {
        if (!(mob instanceof Wither wither)) return;
        for (Wither.Head head : Wither.Head.values()) wither.setTarget(head, null);
    }

    private Optional<UUID> sentryId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(sentryIdKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<UUID> vexParentId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(vexParentKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}

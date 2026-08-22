package dev.liamtolkkinen.sanctuary.sentry;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryService {
    public static final Duration RESPAWN_COOLDOWN = Duration.ofSeconds(30);
    public static final Duration RECALL_TIMEOUT = Duration.ofSeconds(15);
    public static final double HOME_REACHED_DISTANCE = 1.75;
    public static final double BEACON_PROXIMITY_RADIUS = 12.0;

    private final JavaPlugin plugin;
    private final SanctuaryRepository sanctuaryRepository;
    private final SentryRepository repository;
    private final SanctuarySecurityService securityService;
    private final TerritoryPresenceService presenceService;
    private final Logger logger;
    private final NamespacedKey sentryIdKey;
    private final Map<UUID, UUID> authorizedTargets = new HashMap<>();

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
    }

    public Optional<SentryDefinition> definition(org.bukkit.inventory.ItemStack item) {
        return ExtendedItems.getId(item)
            .flatMap(id -> SentryDefinition.ALL.stream().filter(d -> d.itemId().equals(id)).findFirst());
    }

    public Optional<Sanctuary> sanctuaryAt(Location location) throws SQLException {
        List<Sanctuary> candidates = sanctuaryRepository.findActiveInWorld(location.getWorld().getName());
        return presenceService.findCurrentSanctuary(
            candidates,
            location.getWorld().getName(),
            location.getX(),
            location.getZ()
        );
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
        if (!TerritoryCalculator.contains(sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), record.world(), home.getX(), home.getZ())) {
            return record;
        }
        if (!world.isChunkLoaded(record.x() >> 4, record.z() >> 4)) {
            return record;
        }
        if (world.getBlockAt(record.x(), record.y(), record.z()).getType() != ExtendedItems.create(definition.itemId()).getType()) {
            return record;
        }
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
        mob.setTarget(null);
        SentryRecord active = new SentryRecord(record.id(), record.sanctuaryId(), record.itemId(), record.world(), record.x(), record.y(), record.z(),
            Optional.of(mob.getUniqueId()), SentryState.ACTIVE, Optional.empty(), Optional.empty(), record.createdAt(), now);
        repository.save(active);
        return active;
    }

    public void unregister(SentryRecord record) throws SQLException {
        entity(record).ifPresent(Entity::remove);
        authorizedTargets.remove(record.id());
        repository.clearOverrides(record.id());
        repository.delete(record.id());
    }

    public void markDown(SentryRecord record) throws SQLException {
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
        entity(record).ifPresent(entity -> {
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                mob.setAware(!disabled);
                mob.customName(net.kyori.adventure.text.Component.text(
                    definition(record).map(SentryDefinition::displayName).orElse("Sentry") + (disabled ? " [Disabled]" : "")
                ));
                mob.setCustomNameVisible(disabled);
                if (!disabled) moveHome(mob, record);
            }
        });
        authorizedTargets.remove(record.id());
        repository.save(new SentryRecord(record.id(), record.sanctuaryId(), record.itemId(), record.world(), record.x(), record.y(), record.z(), entityId,
            state, Optional.empty(), Optional.empty(), record.createdAt(), now));
    }

    public void recall(SentryRecord record) throws SQLException {
        if (record.state() == SentryState.DOWN) return;
        Instant now = Instant.now();
        authorizedTargets.remove(record.id());
        entity(record).ifPresent(entity -> {
            if (entity instanceof Mob mob) {
                mob.setAware(true);
                mob.setTarget(null);
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
        if (target == null || target.isDead()) return;
        if (target instanceof Player player) {
            SanctuaryRelationship relationship = securityService.relationship(sanctuary, player.getUniqueId());
            if (relationship == SanctuaryRelationship.OWNER || relationship == SanctuaryRelationship.TRUSTED) return;
        }
        for (SentryRecord sentry : repository.findBySanctuary(sanctuary.id())) {
            if (sentry.state() != SentryState.ACTIVE || !effective(sentry, trigger)) continue;
            SentryDefinition definition = definition(sentry).orElse(null);
            Mob mob = entity(sentry).filter(Mob.class::isInstance).map(Mob.class::cast).orElse(null);
            if (definition == null || mob == null || !validTarget(sanctuary, sentry, definition, target)) continue;
            var path = mob.getPathfinder().findPath(target);
            if (path == null || path.getPoints().stream().anyMatch(point ->
                !TerritoryCalculator.contains(
                    sanctuary.position().orElseThrow(),
                    sanctuary.territoryRadius(),
                    sentry.world(),
                    point.getX(),
                    point.getZ()
                )
            )) {
                continue;
            }
            authorizedTargets.put(sentry.id(), target.getUniqueId());
            mob.setAware(true);
            mob.setTarget(target);
            mob.getPathfinder().moveTo(path, 1.0);
        }
    }

    public boolean isManaged(Entity entity) {
        return sentryId(entity).isPresent();
    }

    public Optional<SentryRecord> record(Entity entity) throws SQLException {
        Optional<UUID> id = sentryId(entity);
        return id.isPresent() ? repository.findById(id.orElseThrow()) : repository.findByEntity(entity.getUniqueId());
    }

    public boolean targetAllowed(Mob mob, LivingEntity target) throws SQLException {
        SentryRecord record = record(mob).orElse(null);
        if (record == null) return true;
        return target != null && target.getUniqueId().equals(authorizedTargets.get(record.id()));
    }

    public void clearTarget(SentryRecord sentry) {
        authorizedTargets.remove(sentry.id());
        entity(sentry).filter(Mob.class::isInstance).map(Mob.class::cast).ifPresent(mob -> {
            mob.setTarget(null);
            moveHome(mob, sentry);
        });
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
                !TerritoryCalculator.contains(
                    sanctuary.position().orElseThrow(),
                    sanctuary.territoryRadius(),
                    record.world(),
                    point.getX(),
                    point.getZ()
                )
            );
            if (!exits) mob.getPathfinder().moveTo(path, 1.0);
        } catch (SQLException exception) {
            logger.warning("Failed to validate sentry home path: " + exception.getMessage());
        }
    }

    public boolean canManage(Player player, Sanctuary sanctuary) {
        return sanctuary.ownerId().equals(player.getUniqueId())
            || (sanctuary.debugEphemeral() && player.hasPermission("sanctuary.admin"));
    }

    private boolean validTarget(Sanctuary sanctuary, SentryRecord sentry, SentryDefinition definition, LivingEntity target) {
        if (target.getWorld() != Bukkit.getWorld(sentry.world()) || sanctuary.position().isEmpty()) return false;
        Location t = target.getLocation();
        if (!TerritoryCalculator.contains(sanctuary.position().orElseThrow(), sanctuary.territoryRadius(), sentry.world(), t.getX(), t.getZ())) return false;
        Location h = home(sentry);
        double dx = t.getX() - h.getX(); double dz = t.getZ() - h.getZ();
        return dx * dx + dz * dz <= definition.targetRadius() * definition.targetRadius();
    }

    private Optional<UUID> sentryId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(sentryIdKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
}

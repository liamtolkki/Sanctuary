package dev.liamtolkkinen.sanctuary.companion;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.liamtolkkinen.sanctuary.defense.DefenseTargetingRules;
import dev.liamtolkkinen.sanctuary.sentry.SentryLoadout;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class CompanionService {
    public static final double HOSTILE_RADIUS = 10.0;
    public static final double FOLLOW_DISTANCE = 7.5;
    public static final double FOLLOW_MAX_DISTANCE = 10.0;
    public static final double TELEPORT_DISTANCE = 28.0;
    public static final double DEFENSE_MAX_DISTANCE = 32.0;
    public static final double STAY_COMBAT_RADIUS = 20.0;
    public static final Duration OWNER_THREAT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration COMPANION_THREAT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration COMBAT_RELATIONSHIP_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration VEX_IDLE_TIMEOUT = Duration.ofSeconds(30);

    private static final int TELEPORT_HORIZONTAL_SEARCH_RADIUS = 6;
    private static final int TELEPORT_VERTICAL_SEARCH_RADIUS = 8;
    private static final double TELEPORT_EPSILON = 1.0e-3;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final NamespacedKey companionIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey companionTypeKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey stayWorldKey;
    private final NamespacedKey stayXKey;
    private final NamespacedKey stayYKey;
    private final NamespacedKey stayZKey;
    private final NamespacedKey companionVexParentKey;
    private final NamespacedKey sentryIdKey;
    private final NamespacedKey sentryVexParentKey;
    private final Map<UUID, UUID> authorizedTargets = new HashMap<>();
    private final Map<UUID, Map<UUID, Instant>> ownerThreats = new HashMap<>();
    private final Map<UUID, Map<UUID, Instant>> companionThreats = new HashMap<>();
    private final Map<UUID, Map<UUID, Instant>> combatRelationships = new HashMap<>();
    private final Map<UUID, Instant> vexActivity = new HashMap<>();
    private final Set<UUID> authorizedTeleports = new HashSet<>();

    public CompanionService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.companionIdKey = new NamespacedKey(plugin, "companion_id");
        this.ownerIdKey = new NamespacedKey(plugin, "companion_owner");
        this.companionTypeKey = new NamespacedKey(plugin, "companion_type");
        this.modeKey = new NamespacedKey(plugin, "companion_mode");
        this.stayWorldKey = new NamespacedKey(plugin, "companion_stay_world");
        this.stayXKey = new NamespacedKey(plugin, "companion_stay_x");
        this.stayYKey = new NamespacedKey(plugin, "companion_stay_y");
        this.stayZKey = new NamespacedKey(plugin, "companion_stay_z");
        this.companionVexParentKey = new NamespacedKey(plugin, "companion_vex_parent");
        this.sentryIdKey = new NamespacedKey(plugin, "sentry_id");
        this.sentryVexParentKey = new NamespacedKey(plugin, "sentry_vex_parent");
    }

    public Optional<CompanionDefinition> definition(ItemStack item) {
        return CompanionDefinition.fromItem(item);
    }

    public Optional<CompanionDefinition> definition(Entity entity) {
        String value = entity.getPersistentDataContainer().get(
            companionTypeKey,
            PersistentDataType.STRING
        );
        return value == null ? Optional.empty() : CompanionDefinition.byPersistentId(value);
    }

    public Mob spawn(Player owner, CompanionDefinition definition, Location location) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Companion spawn location must have a world");
        }

        Entity spawned = location.getWorld().spawnEntity(location, definition.entityType());
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            throw new IllegalStateException(
                "Companion type is not a Mob: " + definition.entityType()
            );
        }

        UUID companionId = UUID.randomUUID();
        PersistentDataContainer data = mob.getPersistentDataContainer();
        data.set(companionIdKey, PersistentDataType.STRING, companionId.toString());
        data.set(ownerIdKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(companionTypeKey, PersistentDataType.STRING, definition.persistentId());
        data.set(modeKey, PersistentDataType.STRING, CompanionMode.FOLLOW.name());

        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.customName(Component.text(definition.displayName()));
        mob.setCustomNameVisible(false);

        if (definition.baby() && mob instanceof Zombie zombie) {
            zombie.setBaby();
        }
        if (mob instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (mob instanceof Wither wither) {
            wither.setInvulnerabilityTicks(0);
        }

        SentryLoadout.apply(mob);
        configureManagedAi(mob);
        return mob;
    }

    public void configureManagedAi(Mob mob) {
        Bukkit.getMobGoals().removeAllGoals(mob, GoalType.TARGET);
        mob.setAware(true);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getPathfinder().stopPathfinding();
        clearWitherTargets(mob);
        clearWardenAnger(mob);
        if (mob instanceof Enderman enderman) {
            enderman.setCarriedBlock(null);
        }
        if (mob instanceof Slime slime) {
            slime.setWander(false);
        }
        if (mob instanceof Creeper creeper) {
            creeper.setIgnited(false);
            creeper.setFuseTicks(0);
        }
    }

    public boolean isManaged(Entity entity) {
        return companionId(entity).isPresent() && ownerId(entity).isPresent();
    }

    public boolean isCompanionVex(Entity entity) {
        return companionVexParentId(entity).isPresent();
    }

    public boolean isCompanionForce(Entity entity) {
        return isManaged(entity) || isCompanionVex(entity);
    }

    public boolean isProtectedSanctuaryDefenseEntity(Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return data.has(sentryIdKey, PersistentDataType.STRING)
            || data.has(sentryVexParentKey, PersistentDataType.STRING);
    }

    public boolean isSanctuaryDefenseEntity(Entity entity) {
        return isCompanionForce(entity) || isProtectedSanctuaryDefenseEntity(entity);
    }

    public Optional<UUID> companionId(Entity entity) {
        return readUuid(entity, companionIdKey);
    }

    public Optional<UUID> ownerId(Entity entity) {
        return readUuid(entity, ownerIdKey);
    }

    public Optional<Player> owner(Entity entity) {
        return ownerId(entity)
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .filter(Player::isOnline);
    }

    public Optional<UUID> combatOwnerId(Entity entity) {
        if (entity instanceof Player player) {
            return Optional.of(player.getUniqueId());
        }
        Optional<UUID> directOwner = ownerId(entity);
        if (directOwner.isPresent()) {
            return directOwner;
        }
        return companionVexParentId(entity)
            .flatMap(this::loadedCompanionById)
            .flatMap(this::ownerId);
    }

    public Optional<Player> combatOwner(Entity entity) {
        return combatOwnerId(entity)
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .filter(Player::isOnline);
    }

    public boolean isOwnedCompanionForce(Player owner, Entity entity) {
        return isCompanionForce(entity)
            && combatOwnerId(entity)
                .filter(owner.getUniqueId()::equals)
                .isPresent();
    }

    public boolean isOwner(Player player, Entity companion) {
        return ownerId(companion)
            .filter(player.getUniqueId()::equals)
            .isPresent();
    }

    public CompanionMode mode(Entity entity) {
        String value = entity.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        if (value == null) {
            return CompanionMode.FOLLOW;
        }
        try {
            return CompanionMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CompanionMode.FOLLOW;
        }
    }

    public CompanionMode toggleMode(Mob companion) {
        CompanionMode next = mode(companion) == CompanionMode.FOLLOW
            ? CompanionMode.STAY
            : CompanionMode.FOLLOW;
        setMode(companion, next);
        return next;
    }

    public void setMode(Mob companion, CompanionMode mode) {
        PersistentDataContainer data = companion.getPersistentDataContainer();
        data.set(modeKey, PersistentDataType.STRING, mode.name());
        if (mode == CompanionMode.STAY) {
            Location location = companion.getLocation();
            data.set(stayWorldKey, PersistentDataType.STRING, location.getWorld().getName());
            data.set(stayXKey, PersistentDataType.DOUBLE, location.getX());
            data.set(stayYKey, PersistentDataType.DOUBLE, location.getY());
            data.set(stayZKey, PersistentDataType.DOUBLE, location.getZ());
        } else {
            data.remove(stayWorldKey);
            data.remove(stayXKey);
            data.remove(stayYKey);
            data.remove(stayZKey);
        }
        clearTarget(companion);
        companion.getPathfinder().stopPathfinding();
        companion.setVelocity(new Vector(0, 0, 0));
        if (mode == CompanionMode.STAY) {
            companion.setAware(false);
        }
    }

    public Optional<Location> stayLocation(Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String worldName = data.get(stayWorldKey, PersistentDataType.STRING);
        Double x = data.get(stayXKey, PersistentDataType.DOUBLE);
        Double y = data.get(stayYKey, PersistentDataType.DOUBLE);
        Double z = data.get(stayZKey, PersistentDataType.DOUBLE);
        if (worldName == null || x == null || y == null || z == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(new Location(world, x, y, z));
    }

    public List<Mob> loadedCompanions() {
        List<Mob> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (isManaged(mob)) {
                    result.add(mob);
                }
            }
        }
        return result;
    }

    public void noteOwnerAttacked(Player owner, LivingEntity attacker, Instant now) {
        if (!canRememberThreat(owner, attacker)) {
            return;
        }
        rememberThreat(
            ownerThreats,
            owner.getUniqueId(),
            attacker.getUniqueId(),
            now.plus(OWNER_THREAT_TIMEOUT)
        );
    }

    public void noteCompanionAttacked(Player owner, LivingEntity attacker, Instant now) {
        if (!canRememberThreat(owner, attacker)) {
            return;
        }
        rememberThreat(
            companionThreats,
            owner.getUniqueId(),
            attacker.getUniqueId(),
            now.plus(COMPANION_THREAT_TIMEOUT)
        );
    }

    public void noteCombatRelationship(
        LivingEntity first,
        LivingEntity second,
        Instant now
    ) {
        UUID firstOwner = combatOwnerId(first).orElse(null);
        UUID secondOwner = combatOwnerId(second).orElse(null);
        if (firstOwner == null || secondOwner == null || firstOwner.equals(secondOwner)) {
            return;
        }

        Instant expiresAt = now.plus(COMBAT_RELATIONSHIP_TIMEOUT);
        rememberRelationship(firstOwner, secondOwner, expiresAt);
        rememberRelationship(secondOwner, firstOwner, expiresAt);
    }

    public LivingEntity findTarget(Mob companion, Player owner, Instant now) {
        LivingEntity targetingOwner = owner.getNearbyEntities(
                DEFENSE_MAX_DISTANCE,
                DEFENSE_MAX_DISTANCE,
                DEFENSE_MAX_DISTANCE
            )
            .stream()
            .filter(Mob.class::isInstance)
            .map(Mob.class::cast)
            .filter(mob -> isTargetingOwner(mob, owner))
            .filter(mob -> eligibleTarget(companion, owner, mob))
            .min(Comparator.comparingDouble(
                mob -> mob.getLocation().distanceSquared(owner.getLocation())
            ))
            .orElse(null);
        if (targetingOwner != null) {
            return targetingOwner;
        }

        LivingEntity recentOwnerAttacker = closestRememberedThreat(
            companion,
            owner,
            ownerThreats,
            now
        );
        if (recentOwnerAttacker != null) {
            return recentOwnerAttacker;
        }

        LivingEntity deliberateTarget = CompanionCombatMemory.target(owner, now)
            .filter(target -> eligibleTarget(companion, owner, target))
            .orElse(null);
        if (deliberateTarget != null) {
            return deliberateTarget;
        }

        LivingEntity companionAttacker = closestRememberedThreat(
            companion,
            owner,
            companionThreats,
            now
        );
        if (companionAttacker != null) {
            return companionAttacker;
        }

        LivingEntity combatOpponent = closestCombatOpponent(companion, owner, now);
        if (combatOpponent != null) {
            return combatOpponent;
        }

        Location center = mode(companion) == CompanionMode.STAY
            ? stayLocation(companion).orElse(companion.getLocation())
            : owner.getLocation();
        if (center.getWorld() == null) {
            return null;
        }
        return center.getWorld().getNearbyEntities(
                center,
                HOSTILE_RADIUS,
                HOSTILE_RADIUS,
                HOSTILE_RADIUS
            )
            .stream()
            .filter(Enemy.class::isInstance)
            .map(Enemy.class::cast)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(target -> !isCompanionForce(target))
            .filter(target -> eligibleTarget(companion, owner, target))
            .min(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(center)))
            .orElse(null);
    }

    public boolean validTarget(Mob companion, Player owner, LivingEntity target) {
        if (target.isDead()
            || target.getUniqueId().equals(owner.getUniqueId())
            || target.getWorld() != owner.getWorld()
            || isProtectedSanctuaryDefenseEntity(target)
            || isOwnedCompanionForce(owner, target)) {
            return false;
        }

        double ownerDistanceSquared = target.getLocation().distanceSquared(owner.getLocation());
        if (ownerDistanceSquared > DEFENSE_MAX_DISTANCE * DEFENSE_MAX_DISTANCE) {
            return false;
        }

        if (mode(companion) == CompanionMode.STAY) {
            Location stay = stayLocation(companion).orElse(companion.getLocation());
            if (stay.getWorld() != target.getWorld()) {
                return false;
            }
            if (stay.distanceSquared(target.getLocation()) > STAY_COMBAT_RADIUS * STAY_COMBAT_RADIUS) {
                return false;
            }
        }
        return true;
    }

    public void authorizeTarget(Mob companion, LivingEntity target) {
        UUID id = companionId(companion).orElse(null);
        if (id != null) {
            authorizedTargets.put(id, target.getUniqueId());
        }
    }

    public Optional<LivingEntity> authorizedTarget(Entity companion) {
        UUID id = companionId(companion).orElse(null);
        if (id == null) {
            UUID parentId = companionVexParentId(companion).orElse(null);
            if (parentId == null) {
                return Optional.empty();
            }
            id = parentId;
        }
        UUID targetId = authorizedTargets.get(id);
        if (targetId == null) {
            return Optional.empty();
        }
        Entity target = Bukkit.getEntity(targetId);
        return target instanceof LivingEntity living && !living.isDead()
            ? Optional.of(living)
            : Optional.empty();
    }

    public boolean targetAllowed(Mob mob, LivingEntity target) {
        if (target == null || isProtectedSanctuaryDefenseEntity(target)) {
            return false;
        }
        UUID attackerOwner = combatOwnerId(mob).orElse(null);
        UUID targetOwner = combatOwnerId(target).orElse(null);
        if (attackerOwner != null && attackerOwner.equals(targetOwner)) {
            return false;
        }
        return authorizedTarget(mob)
            .filter(value -> value.getUniqueId().equals(target.getUniqueId()))
            .isPresent();
    }

    public boolean mayDamage(Entity attacker, LivingEntity victim) {
        if (isProtectedSanctuaryDefenseEntity(victim)) {
            return false;
        }

        UUID attackerOwner = combatOwnerId(attacker).orElse(null);
        if (attackerOwner != null && attackerOwner.equals(victim.getUniqueId())) {
            return false;
        }

        UUID victimOwner = combatOwnerId(victim).orElse(null);
        if (attackerOwner != null && attackerOwner.equals(victimOwner)) {
            return false;
        }

        return authorizedTarget(attacker)
            .filter(value -> value.getUniqueId().equals(victim.getUniqueId()))
            .isPresent();
    }

    public void maintainTarget(Mob companion, LivingEntity target, Instant now) {
        companion.setAware(true);
        companion.setAggressive(true);
        if (companion.getTarget() == null
            || !companion.getTarget().getUniqueId().equals(target.getUniqueId())) {
            companion.setTarget(target);
        }
        if (companion instanceof Wither wither) {
            for (Wither.Head head : Wither.Head.values()) {
                wither.setTarget(head, target);
            }
        }
        if (companion instanceof Evoker) {
            syncEvokerVexes(companion, target, now);
        }
    }

    public void clearTarget(Mob companion) {
        companionId(companion).ifPresent(authorizedTargets::remove);
        companion.setTarget(null);
        companion.setAggressive(false);
        clearWitherTargets(companion);
        clearWardenAnger(companion);
        if (companion instanceof Creeper creeper) {
            creeper.setIgnited(false);
            creeper.setFuseTicks(0);
        }
        if (companion instanceof Evoker) {
            syncEvokerVexes(companion, null, Instant.now());
        }
    }

    public void idleWithoutOwner(Mob companion) {
        clearTarget(companion);
        companion.getPathfinder().stopPathfinding();
        companion.setVelocity(new Vector(0, 0, 0));
        companion.setAware(false);
    }

    public void idleAtAnchor(Mob companion, Player owner) {
        if (mode(companion) == CompanionMode.FOLLOW) {
            followOwner(companion, owner);
            return;
        }

        Location stay = stayLocation(companion).orElseGet(companion::getLocation);
        if (stay.getWorld() != companion.getWorld()) {
            idleWithoutOwner(companion);
            return;
        }
        if (companion.getLocation().distanceSquared(stay) <= 2.25) {
            companion.getPathfinder().stopPathfinding();
            companion.setVelocity(new Vector(0, 0, 0));
            companion.setAware(false);
            return;
        }
        moveTo(companion, stay, 1.0);
    }

    public void followOwner(Mob companion, Player owner) {
        Location target = formationLocation(owner, companion);
        if (companion.getWorld() != owner.getWorld()) {
            if (owner.isOnGround()) {
                teleportFollower(companion, target);
            }
            return;
        }

        double distanceSquared = companion.getLocation().distanceSquared(owner.getLocation());
        if (distanceSquared > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            if (owner.isOnGround()) {
                teleportFollower(companion, target);
            }
            return;
        }

        if (distanceSquared <= FOLLOW_MAX_DISTANCE * FOLLOW_MAX_DISTANCE) {
            companion.getPathfinder().stopPathfinding();
            companion.setVelocity(new Vector(0, 0, 0));
            companion.setAware(false);
            return;
        }
        moveTo(companion, target, 1.15);
    }

    public void teleportFollowers(Player owner) {
        if (!owner.isOnGround()) {
            return;
        }
        for (Mob companion : loadedCompanions()) {
            if (mode(companion) != CompanionMode.FOLLOW || !isOwner(owner, companion)) {
                continue;
            }
            teleportFollower(companion, formationLocation(owner, companion));
        }
    }

    public void moveTo(Mob mob, Location destination, double speed) {
        if (destination.getWorld() == null || mob.getWorld() != destination.getWorld()) {
            return;
        }
        mob.setAware(true);
        var path = mob.getPathfinder().findPath(destination);
        if (path != null) {
            mob.getPathfinder().moveTo(path, speed);
        }
    }

    public Location formationLocation(Player owner, Entity companion) {
        UUID id = companionId(companion).orElse(companion.getUniqueId());
        long bits = id.getLeastSignificantBits();
        double angle = ((bits & 0xFFFFL) / 65535.0) * Math.PI * 2.0;
        double radius = FOLLOW_DISTANCE + (((bits >>> 16) & 0x3L) * 0.6);
        return owner.getLocation().clone().add(
            Math.cos(angle) * radius,
            0.0,
            Math.sin(angle) * radius
        );
    }

    public boolean teleportDestinationAllowed(Entity entity, Location destination) {
        if (authorizedTeleports.contains(entity.getUniqueId())) {
            return true;
        }
        if (!isManaged(entity) || !(entity instanceof Enderman) || authorizedTarget(entity).isEmpty()) {
            return false;
        }

        if (mode(entity) == CompanionMode.STAY) {
            Location stay = stayLocation(entity).orElse(entity.getLocation());
            return stay.getWorld() == destination.getWorld()
                && stay.distanceSquared(destination) <= STAY_COMBAT_RADIUS * STAY_COMBAT_RADIUS;
        }

        Player owner = owner(entity).orElse(null);
        return owner != null
            && owner.getWorld() == destination.getWorld()
            && owner.getLocation().distanceSquared(destination)
                <= DEFENSE_MAX_DISTANCE * DEFENSE_MAX_DISTANCE;
    }

    public boolean isAuthorizedTeleport(Entity entity) {
        return authorizedTeleports.contains(entity.getUniqueId());
    }

    public void keepAlive(Mob companion) {
        companion.setRemainingAir(companion.getMaximumAir());
        if (companion instanceof Enderman enderman) {
            enderman.setCarriedBlock(null);
        }
        if (companion instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
    }

    public boolean ensureEvokerVex(Vex vex) {
        UUID existing = companionVexParentId(vex).orElse(null);
        if (existing != null) {
            Mob parent = loadedCompanionById(existing).orElse(null);
            if (!(parent instanceof Evoker)) {
                vex.getPersistentDataContainer().remove(companionVexParentKey);
                return false;
            }
            configureEvokerVex(vex, parent);
            return true;
        }

        Mob summoner = vex.getSummoner();
        if (!(summoner instanceof Evoker) || !isManaged(summoner)) {
            return false;
        }
        UUID parentId = companionId(summoner).orElseThrow();
        vex.getPersistentDataContainer().set(
            companionVexParentKey,
            PersistentDataType.STRING,
            parentId.toString()
        );
        vexActivity.putIfAbsent(parentId, Instant.now());
        configureEvokerVex(vex, summoner);
        return true;
    }

    public void tickEvokerVexes(Mob parent, LivingEntity target, Instant now) {
        if (!(parent instanceof Evoker)) {
            return;
        }
        syncEvokerVexes(parent, target, now);
        UUID parentId = companionId(parent).orElse(null);
        if (parentId == null || target != null && !target.isDead()) {
            return;
        }
        Instant lastActivity = vexActivity.computeIfAbsent(parentId, ignored -> now);
        if (!now.isBefore(lastActivity.plus(VEX_IDLE_TIMEOUT))) {
            removeEvokerVexes(parentId);
        }
    }

    public void removeCompanion(Mob companion) {
        UUID id = companionId(companion).orElse(null);
        if (id != null) {
            authorizedTargets.remove(id);
            vexActivity.remove(id);
            removeEvokerVexes(id);
        }
    }

    public boolean hasManagedWardenFor(Player player) {
        for (Mob companion : loadedCompanions()) {
            if (companion instanceof Warden
                && isOwner(player, companion)
                && companion.getWorld() == player.getWorld()
                && companion.getLocation().distanceSquared(player.getLocation()) <= 4096.0) {
                return true;
            }
        }
        return false;
    }

    private boolean canRememberThreat(Player owner, LivingEntity attacker) {
        return !owner.getUniqueId().equals(attacker.getUniqueId())
            && !isProtectedSanctuaryDefenseEntity(attacker)
            && !isOwnedCompanionForce(owner, attacker);
    }

    private void rememberThreat(
        Map<UUID, Map<UUID, Instant>> memory,
        UUID ownerId,
        UUID attackerId,
        Instant expiresAt
    ) {
        memory.computeIfAbsent(ownerId, ignored -> new HashMap<>())
            .put(attackerId, expiresAt);
    }

    private void rememberRelationship(UUID ownerId, UUID opponentId, Instant expiresAt) {
        combatRelationships.computeIfAbsent(ownerId, ignored -> new HashMap<>())
            .put(opponentId, expiresAt);
    }

    private boolean isTargetingOwner(Mob mob, Player owner) {
        LivingEntity target = mob.getTarget();
        if (target != null && target.getUniqueId().equals(owner.getUniqueId())) {
            return true;
        }
        return isCompanionForce(mob)
            && authorizedTarget(mob)
                .filter(value -> value.getUniqueId().equals(owner.getUniqueId()))
                .isPresent();
    }

    private LivingEntity closestRememberedThreat(
        Mob companion,
        Player owner,
        Map<UUID, Map<UUID, Instant>> memory,
        Instant now
    ) {
        Map<UUID, Instant> threats = memory.get(owner.getUniqueId());
        if (threats == null) {
            return null;
        }

        threats.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        if (threats.isEmpty()) {
            memory.remove(owner.getUniqueId());
            return null;
        }

        return threats.keySet().stream()
            .map(Bukkit::getEntity)
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(target -> !target.isDead())
            .filter(target -> eligibleTarget(companion, owner, target))
            .min(Comparator.comparingDouble(
                target -> target.getLocation().distanceSquared(owner.getLocation())
            ))
            .orElse(null);
    }

    private LivingEntity closestCombatOpponent(Mob companion, Player owner, Instant now) {
        Set<UUID> opponents = activeCombatOpponents(owner.getUniqueId(), now);
        if (opponents.isEmpty()) {
            return null;
        }

        return owner.getNearbyEntities(
                DEFENSE_MAX_DISTANCE,
                DEFENSE_MAX_DISTANCE,
                DEFENSE_MAX_DISTANCE
            )
            .stream()
            .filter(LivingEntity.class::isInstance)
            .map(LivingEntity.class::cast)
            .filter(target -> target instanceof Player || isCompanionForce(target))
            .filter(target -> combatOwnerId(target).filter(opponents::contains).isPresent())
            .filter(target -> eligibleTarget(companion, owner, target))
            .min(Comparator.comparingDouble(
                target -> target.getLocation().distanceSquared(owner.getLocation())
            ))
            .orElse(null);
    }

    private Set<UUID> activeCombatOpponents(UUID ownerId, Instant now) {
        Map<UUID, Instant> relationships = combatRelationships.get(ownerId);
        if (relationships == null) {
            return Set.of();
        }

        relationships.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        if (relationships.isEmpty()) {
            combatRelationships.remove(ownerId);
            return Set.of();
        }
        return Set.copyOf(relationships.keySet());
    }

    private boolean eligibleTarget(Mob companion, Player owner, LivingEntity target) {
        if (!validTarget(companion, owner, target)) {
            return false;
        }

        Location center = mode(companion) == CompanionMode.STAY
            ? stayLocation(companion).orElse(companion.getLocation())
            : owner.getLocation();
        if (!DefenseTargetingRules.isLocallyRelevant(companion, center, target)) {
            return false;
        }

        boolean strictAquatic = definition(companion)
            .map(CompanionDefinition::requiresWaterSpawn)
            .orElse(false);
        return !strictAquatic || target.getLocation().getBlock().getType() == Material.WATER;
    }

    private void syncEvokerVexes(Mob parent, LivingEntity target, Instant now) {
        if (!(parent instanceof Evoker)) {
            return;
        }
        UUID parentId = companionId(parent).orElse(null);
        if (parentId == null) {
            return;
        }

        for (Vex vex : parent.getWorld().getEntitiesByClass(Vex.class)) {
            if (vex.getSummoner() == parent) {
                ensureEvokerVex(vex);
            }
        }
        if (target != null && !target.isDead()) {
            vexActivity.put(parentId, now);
        }
        for (Vex vex : evokerVexes(parentId)) {
            vex.setBound(parent.getLocation());
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

    private void configureEvokerVex(Vex vex, Mob parent) {
        vex.setPersistent(true);
        Bukkit.getMobGoals().removeAllGoals(vex, GoalType.TARGET);
        vex.setBound(parent.getLocation());
        vex.setLimitedLifetime(false);
    }

    private List<Vex> evokerVexes(UUID parentId) {
        List<Vex> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Vex vex : world.getEntitiesByClass(Vex.class)) {
                if (companionVexParentId(vex).filter(parentId::equals).isPresent()) {
                    result.add(vex);
                }
            }
        }
        return result;
    }

    private void removeEvokerVexes(UUID parentId) {
        for (Vex vex : evokerVexes(parentId)) {
            vex.remove();
        }
    }

    private Optional<Mob> loadedCompanionById(UUID id) {
        return loadedCompanions().stream()
            .filter(mob -> companionId(mob).filter(id::equals).isPresent())
            .findFirst();
    }

    private Optional<UUID> companionVexParentId(Entity entity) {
        return readUuid(entity, companionVexParentKey);
    }

    private Optional<UUID> readUuid(Entity entity, NamespacedKey key) {
        String value = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void teleportFollower(Mob companion, Location preferredDestination) {
        findSafeTeleportDestination(companion, preferredDestination)
            .ifPresent(destination -> teleportManaged(companion, destination));
    }

    private Optional<Location> findSafeTeleportDestination(
        Entity entity,
        Location preferredDestination
    ) {
        World world = preferredDestination.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        boolean aquatic = definition(entity)
            .map(CompanionDefinition::requiresWaterSpawn)
            .orElse(false);
        int baseX = preferredDestination.getBlockX();
        int baseY = preferredDestination.getBlockY();
        int baseZ = preferredDestination.getBlockZ();

        for (int radius = 0; radius <= TELEPORT_HORIZONTAL_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Optional<Location> safe = findSafeTeleportInColumn(
                        entity,
                        world,
                        baseX + dx,
                        baseY,
                        baseZ + dz,
                        preferredDestination,
                        aquatic
                    );
                    if (safe.isPresent()) {
                        return safe;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Location> findSafeTeleportInColumn(
        Entity entity,
        World world,
        int x,
        int baseY,
        int z,
        Location orientation,
        boolean aquatic
    ) {
        for (int offset = 0; offset <= TELEPORT_VERTICAL_SEARCH_RADIUS; offset++) {
            Location above = teleportCandidate(world, x, baseY + offset, z, orientation);
            if (isSafeTeleportDestination(entity, above, aquatic)) {
                return Optional.of(above);
            }
            if (offset == 0) {
                continue;
            }
            Location below = teleportCandidate(world, x, baseY - offset, z, orientation);
            if (isSafeTeleportDestination(entity, below, aquatic)) {
                return Optional.of(below);
            }
        }
        return Optional.empty();
    }

    private Location teleportCandidate(
        World world,
        int x,
        int y,
        int z,
        Location orientation
    ) {
        return new Location(
            world,
            x + 0.5,
            y + TELEPORT_EPSILON,
            z + 0.5,
            orientation.getYaw(),
            orientation.getPitch()
        );
    }

    private boolean isSafeTeleportDestination(Entity entity, Location destination, boolean aquatic) {
        World world = destination.getWorld();
        if (world == null) {
            return false;
        }

        double halfWidth = Math.max(0.1, entity.getWidth() * 0.5);
        double height = Math.max(0.1, entity.getHeight());
        int minX = floorBlock(destination.getX() - halfWidth + TELEPORT_EPSILON);
        int maxX = floorBlock(destination.getX() + halfWidth - TELEPORT_EPSILON);
        int minY = floorBlock(destination.getY());
        int maxY = floorBlock(destination.getY() + height - TELEPORT_EPSILON);
        int minZ = floorBlock(destination.getZ() - halfWidth + TELEPORT_EPSILON);
        int maxZ = floorBlock(destination.getZ() + halfWidth - TELEPORT_EPSILON);

        if (minY < world.getMinHeight() || maxY >= world.getMaxHeight()) {
            return false;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (aquatic) {
                        if (block.getType() != Material.WATER) {
                            return false;
                        }
                    } else if (!block.isPassable() || isHazardousBodyBlock(block.getType())) {
                        return false;
                    }
                }
            }
        }

        if (aquatic) {
            return true;
        }
        if (minY <= world.getMinHeight()) {
            return false;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block support = world.getBlockAt(x, minY - 1, z);
                if (support.isPassable()
                    || isHazardousSupportBlock(support.getType())
                    || support.getBoundingBox().getMaxY() > destination.getY() + TELEPORT_EPSILON) {
                    return false;
                }
            }
        }
        return true;
    }

    private int floorBlock(double value) {
        return (int) Math.floor(value);
    }

    private boolean isHazardousBodyBlock(Material material) {
        return material == Material.LAVA
            || material == Material.FIRE
            || material == Material.SOUL_FIRE
            || material == Material.POWDER_SNOW
            || material == Material.SWEET_BERRY_BUSH
            || material == Material.WITHER_ROSE
            || material == Material.POINTED_DRIPSTONE;
    }

    private boolean isHazardousSupportBlock(Material material) {
        return isHazardousBodyBlock(material)
            || material == Material.MAGMA_BLOCK
            || material == Material.CAMPFIRE
            || material == Material.SOUL_CAMPFIRE
            || material == Material.CACTUS;
    }

    private void teleportManaged(Entity entity, Location destination) {
        if (destination.getWorld() == null) {
            return;
        }
        authorizedTeleports.add(entity.getUniqueId());
        try {
            entity.teleport(destination);
        } finally {
            authorizedTeleports.remove(entity.getUniqueId());
        }
    }

    private void clearWitherTargets(Mob mob) {
        if (!(mob instanceof Wither wither)) {
            return;
        }
        for (Wither.Head head : Wither.Head.values()) {
            wither.setTarget(head, null);
        }
    }

    private void clearWardenAnger(Mob mob) {
        if (!(mob instanceof Warden warden)) {
            return;
        }
        LivingEntity angryAt = warden.getEntityAngryAt();
        if (angryAt != null) {
            warden.clearAnger(angryAt);
        }
    }
}

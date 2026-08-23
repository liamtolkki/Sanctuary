package dev.liamtolkkinen.sanctuary.companion;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class CompanionTask implements Runnable {
    private static final double CREEPER_EMERGENCY_RANGE = 4.0;
    private static final float CREEPER_BLAST_POWER = 4.0f;

    private static final double FRIENDLY_MELEE_RANGE = 3.0;
    private static final Duration FRIENDLY_MELEE_COOLDOWN = Duration.ofMillis(1000);

    private static final double WARDEN_MELEE_RANGE = 3.0;
    private static final double WARDEN_SONIC_MIN_HORIZONTAL_RANGE = 15.0;
    private static final double WARDEN_SONIC_VERTICAL_RANGE = 20.0;
    private static final double WARDEN_SONIC_DAMAGE = 10.0;
    private static final double WARDEN_SONIC_HORIZONTAL_KNOCKBACK = 2.5;
    private static final double WARDEN_SONIC_VERTICAL_KNOCKBACK = 0.5;
    private static final Duration WARDEN_MELEE_COOLDOWN = Duration.ofMillis(1000);
    private static final Duration WARDEN_SONIC_CHARGE_TIME = Duration.ofMillis(1700);
    private static final Duration WARDEN_SONIC_COOLDOWN = Duration.ofSeconds(2);

    private static final double DOLPHINS_GRACE_RANGE = 10.0;
    private static final int DOLPHINS_GRACE_DURATION_TICKS = 40;

    private final CompanionService service;
    private final Logger logger;
    private final Map<UUID, Instant> friendlyLastMeleeAttack = new HashMap<>();
    private final Map<UUID, Instant> wardenLastMeleeAttack = new HashMap<>();
    private final Map<UUID, Instant> wardenSonicChargeStarted = new HashMap<>();
    private final Map<UUID, Instant> wardenSonicCooldownUntil = new HashMap<>();

    public CompanionTask(CompanionService service, Logger logger) {
        this.service = service;
        this.logger = logger;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 10L, 10L);
    }

    @Override
    public void run() {
        Instant now = Instant.now();
        try {
            for (Mob companion : service.loadedCompanions()) {
                tickCompanion(companion, now);
            }
        } catch (RuntimeException exception) {
            logger.warning("Failed companion maintenance tick: " + exception.getMessage());
        }
    }

    private void tickCompanion(Mob companion, Instant now) {
        service.keepAlive(companion);
        UUID companionId = service.companionId(companion).orElse(companion.getUniqueId());
        Player owner = service.owner(companion).orElse(null);
        if (owner == null || owner.isDead()) {
            clearSpecialCombat(companionId);
            service.idleWithoutOwner(companion);
            service.tickEvokerVexes(companion, null, now);
            return;
        }

        boolean strictAquatic = service.definition(companion)
            .map(CompanionDefinition::requiresWaterSpawn)
            .orElse(false);

        if (companion.getType() == EntityType.DOLPHIN) {
            grantDolphinsGrace(companion, owner);
        }

        // Guardian, Elder Guardian, Axolotl and Dolphin companions are strictly
        // aquatic. They stay where they are when their owner leaves the water
        // instead of pathing or teleporting onto land. Drowned are intentionally
        // excluded because their definition is amphibious.
        if (strictAquatic && !isWater(owner.getLocation())) {
            clearSpecialCombat(companionId);
            service.clearTarget(companion);
            service.idleWithoutOwner(companion);
            service.tickEvokerVexes(companion, null, now);
            return;
        }

        LivingEntity target = service.findTarget(companion, owner, now);
        if (strictAquatic && target != null && !isWater(target.getLocation())) {
            target = null;
        }

        if (target == null) {
            clearSpecialCombat(companionId);
            service.clearTarget(companion);
            service.idleAtAnchor(companion, owner);
            service.tickEvokerVexes(companion, null, now);
            return;
        }

        service.authorizeTarget(companion, target);
        if (companion instanceof Creeper creeper) {
            tickCreeperCombat(creeper, owner, target);
            return;
        } else if (companion instanceof Warden warden) {
            tickWardenCombat(companionId, warden, target, now);
        } else if (!(companion instanceof Enemy)) {
            tickFriendlyCombat(companionId, companion, target, now);
        } else {
            service.maintainTarget(companion, target, now);
        }
        service.tickEvokerVexes(companion, target, now);
    }

    private void grantDolphinsGrace(Mob dolphin, Player owner) {
        if (dolphin.getWorld() != owner.getWorld()
            || !isWater(owner.getLocation())
            || dolphin.getLocation().distanceSquared(owner.getLocation())
                > DOLPHINS_GRACE_RANGE * DOLPHINS_GRACE_RANGE) {
            return;
        }

        owner.addPotionEffect(new PotionEffect(
            PotionEffectType.DOLPHINS_GRACE,
            DOLPHINS_GRACE_DURATION_TICKS,
            0,
            true,
            false,
            true
        ));
    }

    private boolean isWater(Location location) {
        return location.getBlock().getType() == Material.WATER;
    }

    private void tickFriendlyCombat(
        UUID companionId,
        Mob companion,
        LivingEntity target,
        Instant now
    ) {
        service.maintainTarget(companion, target, now);
        double distanceSquared = companion.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared > FRIENDLY_MELEE_RANGE * FRIENDLY_MELEE_RANGE) {
            service.moveTo(companion, target.getLocation(), 1.15);
            return;
        }

        companion.getPathfinder().stopPathfinding();
        companion.lookAt(target);
        Instant previousAttack = friendlyLastMeleeAttack.get(companionId);
        if (previousAttack == null
            || !now.isBefore(previousAttack.plus(FRIENDLY_MELEE_COOLDOWN))) {
            companion.attack(target);
            friendlyLastMeleeAttack.put(companionId, now);
        }
    }

    private void tickCreeperCombat(
        Creeper creeper,
        Player owner,
        LivingEntity target
    ) {
        double ownerDistanceSquared = owner.getLocation().distanceSquared(target.getLocation());
        if (ownerDistanceSquared > CREEPER_EMERGENCY_RANGE * CREEPER_EMERGENCY_RANGE) {
            service.clearTarget(creeper);
            service.idleAtAnchor(creeper, owner);
            return;
        }

        creeper.getPathfinder().stopPathfinding();
        creeper.setVelocity(new Vector(0, 0, 0));
        creeper.lookAt(target);
        creeper.getWorld().playSound(
            creeper.getLocation(),
            Sound.ENTITY_CREEPER_PRIMED,
            1.0f,
            1.0f
        );

        creeper.setInvulnerable(true);
        creeper.getWorld().createExplosion(
            creeper,
            target.getLocation(),
            CREEPER_BLAST_POWER,
            false,
            false,
            true
        );
        service.removeCompanion(creeper);
        creeper.remove();
    }

    private void tickWardenCombat(
        UUID companionId,
        Warden warden,
        LivingEntity target,
        Instant now
    ) {
        warden.setAware(true);
        warden.setAggressive(true);
        warden.setTarget(null);
        LivingEntity angryAt = warden.getEntityAngryAt();
        if (angryAt != null) {
            warden.clearAnger(angryAt);
        }

        double horizontalDx = target.getX() - warden.getX();
        double horizontalDz = target.getZ() - warden.getZ();
        double horizontalDistanceSquared = horizontalDx * horizontalDx + horizontalDz * horizontalDz;
        double verticalDistance = Math.abs(target.getY() - warden.getY());
        double distanceSquared = warden.getLocation().distanceSquared(target.getLocation());
        double sonicMinimumSquared = WARDEN_SONIC_MIN_HORIZONTAL_RANGE * WARDEN_SONIC_MIN_HORIZONTAL_RANGE;

        Instant chargeStarted = wardenSonicChargeStarted.get(companionId);
        if (chargeStarted != null) {
            boolean stillInSonicRange = horizontalDistanceSquared > sonicMinimumSquared
                && verticalDistance <= WARDEN_SONIC_VERTICAL_RANGE;
            if (!stillInSonicRange) {
                wardenSonicChargeStarted.remove(companionId);
            } else {
                warden.getPathfinder().stopPathfinding();
                warden.lookAt(target);
                if (!now.isBefore(chargeStarted.plus(WARDEN_SONIC_CHARGE_TIME))) {
                    fireWardenSonicBoom(warden, target);
                    wardenSonicChargeStarted.remove(companionId);
                    wardenSonicCooldownUntil.put(
                        companionId,
                        now.plus(WARDEN_SONIC_COOLDOWN)
                    );
                }
                return;
            }
        }

        if (distanceSquared <= WARDEN_MELEE_RANGE * WARDEN_MELEE_RANGE) {
            warden.getPathfinder().stopPathfinding();
            warden.lookAt(target);
            Instant previousAttack = wardenLastMeleeAttack.get(companionId);
            if (previousAttack == null
                || !now.isBefore(previousAttack.plus(WARDEN_MELEE_COOLDOWN))) {
                warden.attack(target);
                wardenLastMeleeAttack.put(companionId, now);
            }
            return;
        }

        if (horizontalDistanceSquared <= sonicMinimumSquared) {
            service.moveTo(warden, target.getLocation(), 1.2);
            return;
        }

        Instant sonicCooldown = wardenSonicCooldownUntil.get(companionId);
        boolean sonicReady = sonicCooldown == null || !now.isBefore(sonicCooldown);
        boolean inSonicRange = verticalDistance <= WARDEN_SONIC_VERTICAL_RANGE;
        if (sonicReady && inSonicRange) {
            warden.getPathfinder().stopPathfinding();
            warden.lookAt(target);
            warden.getWorld().playSound(
                warden.getLocation(),
                Sound.ENTITY_WARDEN_SONIC_CHARGE,
                3.0f,
                1.0f
            );
            wardenSonicChargeStarted.put(companionId, now);
            return;
        }

        service.moveTo(warden, target.getLocation(), 1.2);
    }

    private void fireWardenSonicBoom(Warden warden, LivingEntity target) {
        Location origin = warden.getEyeLocation();
        Location destination = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector delta = destination.toVector().subtract(origin.toVector());
        double distance = delta.length();
        if (distance <= 0.001) {
            return;
        }

        Vector direction = delta.clone().normalize();
        for (double travelled = 1.0; travelled < distance; travelled += 1.5) {
            Location particleLocation = origin.clone().add(
                direction.clone().multiply(travelled)
            );
            warden.getWorld().spawnParticle(
                Particle.SONIC_BOOM,
                particleLocation,
                1,
                0,
                0,
                0,
                0
            );
        }
        warden.getWorld().playSound(
            warden.getLocation(),
            Sound.ENTITY_WARDEN_SONIC_BOOM,
            3.0f,
            1.0f
        );

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

    private void clearSpecialCombat(UUID companionId) {
        friendlyLastMeleeAttack.remove(companionId);
        wardenLastMeleeAttack.remove(companionId);
        wardenSonicChargeStarted.remove(companionId);
        wardenSonicCooldownUntil.remove(companionId);
    }
}

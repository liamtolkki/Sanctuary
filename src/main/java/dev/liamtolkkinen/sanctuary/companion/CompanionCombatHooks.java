package dev.liamtolkkinen.sanctuary.companion;

import java.time.Instant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Tracks owner combat intent, retaliation threats, and short-lived companion brawls. */
public final class CompanionCombatHooks implements Listener {
    private final CompanionService service;

    public CompanionCombatHooks(CompanionService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity attacker = resolveAttacker(event);
        if (attacker == null || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        Instant now = Instant.now();

        if ((service.isManaged(victim) || service.isCompanionVex(victim))
            && !service.isProtectedSanctuaryDefenseEntity(attacker)) {
            service.combatOwner(victim)
                .filter(owner -> !owner.getUniqueId().equals(attacker.getUniqueId()))
                .ifPresent(owner -> service.noteCompanionAttacked(owner, attacker, now));
        }

        if (attacker instanceof Player owner
            && !service.isProtectedSanctuaryDefenseEntity(victim)
            && !service.isOwnedCompanionForce(owner, victim)) {
            CompanionCombatMemory.remember(owner, victim, now);
        }

        service.noteCombatRelationship(attacker, victim, now);
    }

    private static LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity living) {
            return living;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity living) {
                return living;
            }
        }
        if (event.getDamager() instanceof org.bukkit.entity.EvokerFangs fangs
            && fangs.getOwner() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}

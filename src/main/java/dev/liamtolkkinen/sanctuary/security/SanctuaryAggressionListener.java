package dev.liamtolkkinen.sanctuary.security;

import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sentry.SentryRecord;
import dev.liamtolkkinen.sanctuary.sentry.SentryRepository;
import dev.liamtolkkinen.sanctuary.sentry.SentryService;
import dev.liamtolkkinen.sanctuary.sentry.SentryState;
import dev.liamtolkkinen.sanctuary.sentry.SentryTrigger;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Tracks temporary Sanctuary hostility caused by enabled direct-defense triggers. */
public final class SanctuaryAggressionListener implements Listener {
    private final SanctuarySecurityService securityService;
    private final SanctuaryRepository sanctuaryRepository;
    private final SentryRepository sentryRepository;
    private final SentryService sentryService;
    private final AnchorItemService anchorItemService;
    private final Logger logger;

    public SanctuaryAggressionListener(
        SanctuarySecurityService securityService,
        SanctuaryRepository sanctuaryRepository,
        SentryRepository sentryRepository,
        SentryService sentryService,
        AnchorItemService anchorItemService,
        Logger logger
    ) {
        this.securityService = securityService;
        this.sanctuaryRepository = sanctuaryRepository;
        this.sentryRepository = sentryRepository;
        this.sentryService = sentryService;
        this.anchorItemService = anchorItemService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        try {
            Optional<SentryRecord> sentry = sentryService.record(event.getEntity());
            if (sentry.isPresent()) {
                Sanctuary sanctuary = sanctuaryRepository.findById(
                    sentry.orElseThrow().sanctuaryId()
                ).orElse(null);
                if (sanctuary != null) {
                    markIfEnabled(sanctuary, attacker, SentryTrigger.SENTRY_ATTACKED);
                }
                return;
            }

            if (!(event.getEntity() instanceof Player victim)) {
                return;
            }
            Sanctuary sanctuary = sentryService.sanctuaryAt(victim.getLocation()).orElse(null);
            if (sanctuary != null && victim.getUniqueId().equals(sanctuary.ownerId())) {
                markIfEnabled(sanctuary, attacker, SentryTrigger.OWNER_ATTACKED);
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to update Sanctuary aggression after damage", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnchorDamage(BlockDamageEvent event) {
        if (!(event.getBlock().getState() instanceof org.bukkit.block.TileState tileState)) {
            return;
        }
        if (anchorItemService.readBlockMetadata(tileState).isEmpty()) {
            return;
        }

        try {
            Sanctuary sanctuary = sentryService.sanctuaryAt(event.getBlock().getLocation()).orElse(null);
            if (sanctuary != null) {
                markIfEnabled(sanctuary, event.getPlayer(), SentryTrigger.BEACON_ATTACKED);
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to update Sanctuary aggression after anchor damage", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        try {
            securityService.forgiveTemporaryAggression(event.getEntity().getUniqueId());
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to forgive Sanctuary aggression after player death", exception);
        }
    }

    private void markIfEnabled(Sanctuary sanctuary, Player attacker, SentryTrigger trigger)
        throws SQLException {
        if (sanctuary.ownerId().equals(attacker.getUniqueId())) {
            return;
        }
        boolean enabled = false;
        for (SentryRecord sentry : sentryRepository.findBySanctuary(sanctuary.id())) {
            if (sentry.state() == SentryState.ACTIVE && sentryService.effective(sentry, trigger)) {
                enabled = true;
                break;
            }
        }
        if (enabled) {
            securityService.markAggressive(sanctuary, attacker.getUniqueId(), Instant.now());
        }
    }

    private static Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}

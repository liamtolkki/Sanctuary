package dev.liamtolkkinen.sanctuary.sentry;

import com.destroystokyo.paper.event.entity.EndermanEscapeEvent;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public final class SentryListener implements Listener {
    private final SentryService service;
    private final SentryRepository repository;
    private final SanctuaryRepository sanctuaryRepository;
    private final AnchorItemService anchorItemService;
    private final SentryUiService uiService;
    private final Logger logger;

    public SentryListener(SentryService service, SentryRepository repository, SanctuaryRepository sanctuaryRepository,
                          AnchorItemService anchorItemService, SentryUiService uiService, Logger logger) {
        this.service = service;
        this.repository = repository;
        this.sanctuaryRepository = sanctuaryRepository;
        this.anchorItemService = anchorItemService;
        this.uiService = uiService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManagedPathfind(EntityPathfindEvent event) {
        if (!service.isDefenseEntity(event.getEntity())) return;
        try {
            if (!service.pathDestinationAllowed(event.getEntity(), event.getLoc())) event.setCancelled(true);
        } catch (SQLException exception) {
            event.setCancelled(true);
            logger.log(Level.WARNING, "Failed sentry pathfinding validation", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManagedTeleport(EntityTeleportEvent event) {
        if (!service.isManaged(event.getEntity())) return;
        try {
            if (!service.teleportDestinationAllowed(event.getEntity(), event.getTo())) event.setCancelled(true);
        } catch (SQLException exception) {
            event.setCancelled(true);
            logger.log(Level.WARNING, "Failed sentry teleport validation", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManagedEndermanEscape(EndermanEscapeEvent event) {
        if (!service.isManaged(event.getEntity())) return;
        try {
            SentryRecord record = service.record(event.getEntity()).orElse(null);
            if (record == null || event.getReason() == EndermanEscapeEvent.Reason.RUNAWAY || service.authorizedTarget(record).isEmpty()) {
                event.setCancelled(true);
            }
        } catch (SQLException exception) {
            event.setCancelled(true);
            logger.log(Level.WARNING, "Failed sentry Enderman escape validation", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onManagedTransform(EntityTransformEvent event) {
        if (service.isManaged(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<SentryDefinition> definition = service.definition(event.getItemInHand());
        if (definition.isEmpty()) return;
        if (definition.orElseThrow().entityType() == EntityType.PIGLIN_BRUTE
            && event.getBlockPlaced().getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Piglin Brute sentries can only be placed in the Nether.");
            return;
        }
        try {
            Optional<Sanctuary> sanctuary = service.sanctuaryAt(event.getBlockPlaced().getLocation());
            if (sanctuary.isEmpty()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Sentry posts can only be placed inside a Sanctuary.");
                return;
            }
            SentryRecord record = service.register(
                sanctuary.orElseThrow(), definition.orElseThrow(), event.getBlockPlaced().getLocation(), event.getItemInHand());
            if (record.state() == SentryState.DOWN) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + definition.orElseThrow().displayName()
                    + " restored. Its existing respawn cooldown is still active.");
            } else {
                event.getPlayer().sendMessage(ChatColor.GREEN + definition.orElseThrow().displayName()
                    + " registered to " + sanctuary.orElseThrow().name() + ".");
            }
        } catch (SQLException | RuntimeException exception) {
            event.setCancelled(true);
            logger.log(Level.SEVERE, "Failed to register sentry", exception);
            event.getPlayer().sendMessage(ChatColor.RED + "The sentry could not be registered.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBeaconAttack(BlockDamageEvent event) {
        if (!(event.getBlock().getState() instanceof org.bukkit.block.TileState tileState)) return;
        var metadata = anchorItemService.readBlockMetadata(tileState);
        if (metadata.isEmpty()) return;
        try {
            // Resolve through current territory rather than assuming anchor UUID == Sanctuary UUID.
            // Extender anchors deliberately have their own UUID while remaining part of the same Sanctuary.
            Sanctuary sanctuary = service.sanctuaryAt(event.getBlock().getLocation()).orElse(null);
            if (sanctuary != null) service.trigger(sanctuary, SentryTrigger.BEACON_ATTACKED, event.getPlayer());
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry Beacon-attack trigger", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            Optional<SentryRecord> result = repository.findByPost(
                event.getBlock().getWorld().getName(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
            if (result.isEmpty()) {
                triggerPlayerAction(event.getPlayer(), event.getBlock(), SentryTrigger.BLOCK_BROKEN);
                return;
            }
            SentryRecord sentry = result.orElseThrow();
            Sanctuary sanctuary = sanctuaryRepository.findById(sentry.sanctuaryId()).orElse(null);
            if (sanctuary == null || !service.canManage(event.getPlayer(), sanctuary)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Only the Sanctuary owner can pick up this sentry.");
                return;
            }
            event.setDropItems(false);
            event.setExpToDrop(0);
            var pickup = service.pickupItem(sentry);
            service.unregister(sentry);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), pickup);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Sentry unregistered. Its individual behavior was cleared.");
        } catch (SQLException exception) {
            fail(event.getPlayer(), "Failed to process sentry post break", exception);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOtherPlace(BlockPlaceEvent event) {
        if (service.definition(event.getItemInHand()).isPresent()) return;
        try { triggerPlayerAction(event.getPlayer(), event.getBlockPlaced(), SentryTrigger.BLOCK_PLACED); }
        catch (SQLException exception) { logger.log(Level.WARNING, "Failed sentry block-place trigger", exception); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !event.getAction().isRightClick()) return;
        Material type = event.getClickedBlock().getType();
        if (!(type.name().contains("BUTTON") || type.name().contains("LEVER") || type.name().contains("DOOR")
            || type.name().contains("TRAPDOOR") || type.name().contains("GATE")
            || type == Material.REPEATER || type == Material.COMPARATOR)) return;
        try { triggerPlayerAction(event.getPlayer(), event.getClickedBlock(), SentryTrigger.INTERACTION_USED); }
        catch (SQLException exception) { logger.log(Level.WARNING, "Failed sentry interaction trigger", exception); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainer(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var location = event.getInventory().getLocation();
        if (location == null) return;
        try {
            Optional<Sanctuary> sanctuary = service.sanctuaryAt(location);
            if (sanctuary.isPresent()) service.trigger(sanctuary.orElseThrow(), SentryTrigger.CONTAINER_OPENED, player);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry container trigger", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWardenAnger(WardenAngerChangeEvent event) {
        if (!service.isManaged(event.getEntity())) return;
        try {
            if (!(event.getTarget() instanceof LivingEntity living) || !service.targetAllowed(event.getEntity(), living)) {
                event.setCancelled(true);
            }
        } catch (SQLException exception) {
            event.setCancelled(true);
            logger.log(Level.WARNING, "Failed sentry Warden anger validation", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        try {
            if (event.getTarget() != null && service.isDefenseEntity(event.getTarget())) {
                event.setCancelled(true);
                return;
            }
            if (event.getEntity() instanceof Mob mob && service.isDefenseEntity(mob)) {
                if (!(event.getTarget() instanceof LivingEntity living) || !service.targetAllowed(mob, living)) event.setCancelled(true);
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry target validation", exception);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;
        try {
            if (service.isDefenseEntity(attacker) && event.getEntity() instanceof LivingEntity victim
                && !service.mayDamage(attacker, victim)) {
                event.setCancelled(true);
                return;
            }
            Optional<SentryRecord> victimSentry = service.record(event.getEntity());
            if (victimSentry.isPresent()) {
                if (!(attacker instanceof Player)) {
                    event.setCancelled(true);
                    return;
                }
                Sanctuary sanctuary = sanctuaryRepository.findById(victimSentry.orElseThrow().sanctuaryId()).orElse(null);
                if (sanctuary != null) service.trigger(sanctuary, SentryTrigger.SENTRY_ATTACKED, attacker);
                return;
            }
            if (service.isCompanion(event.getEntity()) && !(attacker instanceof Player)) {
                event.setCancelled(true);
                return;
            }
            Optional<Sanctuary> sanctuary = service.sanctuaryAt(event.getEntity().getLocation());
            if (sanctuary.isEmpty()) return;
            Sanctuary s = sanctuary.orElseThrow();
            if (event.getEntity() instanceof Player player
                && player.getUniqueId().equals(s.ownerId())
                && !attacker.getUniqueId().equals(player.getUniqueId())) {
                service.trigger(s, SentryTrigger.OWNER_ATTACKED, attacker);
            }
            if (attacker instanceof Player) service.trigger(s, SentryTrigger.ENTITY_HURT, attacker);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry damage trigger", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        try {
            Optional<SentryRecord> record = service.record(event.getEntity());
            if (record.isEmpty()) return;
            event.getDrops().clear();
            event.setDroppedExp(0);
            service.markDown(SentryDeathTransition.withoutEntity(record.orElseThrow()));
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to put sentry on respawn cooldown", exception);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onExplode(EntityExplodeEvent event) {
        if (service.isManaged(event.getEntity())) {
            event.blockList().clear();
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Projectile projectile
            && projectile.getShooter() instanceof Entity shooter
            && service.isManaged(shooter)) event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!service.isManaged(event.getEntity())) return;
        if (event.getEntity().getType() == EntityType.WITHER || event.getEntity() instanceof Enderman) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWardenPotionEffect(EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.WARDEN) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getNewEffect() == null || event.getNewEffect().getType() != PotionEffectType.DARKNESS) return;
        try {
            if (service.shouldSuppressManagedWardenEffect(player)) event.setCancelled(true);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed sentry Warden Darkness validation", exception);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event) {
        try {
            Optional<SentryRecord> record = service.record(event.getRightClicked());
            if (record.isEmpty()) return;
            Sanctuary sanctuary = sanctuaryRepository.findById(record.orElseThrow().sanctuaryId()).orElse(null);
            if (sanctuary == null || !service.canManage(event.getPlayer(), sanctuary)) return;
            event.setCancelled(true);
            uiService.open(event.getPlayer(), sanctuary, record.orElseThrow());
        } catch (SQLException exception) {
            fail(event.getPlayer(), "Failed to open sentry UI", exception);
        }
    }

    private void triggerPlayerAction(Player player, Block block, SentryTrigger trigger) throws SQLException {
        Optional<Sanctuary> sanctuary = service.sanctuaryAt(block.getLocation());
        if (sanctuary.isPresent()) service.trigger(sanctuary.orElseThrow(), trigger, player);
    }

    private static LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity living) return living;
        }
        return null;
    }

    private void fail(Player player, String message, Exception exception) {
        logger.log(Level.SEVERE, message, exception);
        player.sendMessage(ChatColor.RED + "Sanctuary sentry action failed.");
    }
}

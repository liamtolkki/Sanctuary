package dev.liamtolkkinen.sanctuary.companion;

import com.destroystokyo.paper.event.entity.EndermanEscapeEvent;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.RayTraceResult;

public final class CompanionListener implements Listener {
    private static final double WARDEN_EGG_DROP_CHANCE = 0.125;
    private static final double OPEN_WATER_RAY_TRACE_DISTANCE = 6.0;

    private final CompanionService service;
    private final CompanionUiService uiService;
    private final CompanionEggState eggState;
    private final JavaPlugin plugin;
    private final Logger logger;

    public CompanionListener(
        CompanionService service,
        CompanionUiService uiService,
        CompanionEggState eggState,
        JavaPlugin plugin,
        Logger logger
    ) {
        this.service = service;
        this.uiService = uiService;
        this.eggState = eggState;
        this.plugin = plugin;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseCompanionEgg(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }

        ItemStack item = event.getItem();
        Optional<CompanionDefinition> definition = service.definition(item);
        if (definition.isEmpty()) {
            return;
        }

        // Intercept every custom Companion Egg before vanilla spawn-egg behavior
        // can create an unmanaged mob. Open-water clicks often have no clicked
        // block, so resolving the destination has to happen after cancellation.
        event.setCancelled(true);
        CompanionDefinition companionDefinition = definition.orElseThrow();
        Block clicked = resolveClickedBlock(event, companionDefinition);
        if (clicked == null) {
            event.getPlayer().sendMessage(
                ChatColor.YELLOW
                    + (companionDefinition.requiresWaterSpawn()
                        ? companionDefinition.displayName() + " must be summoned in water."
                        : "Use the Companion Egg on a block to summon it.")
            );
            return;
        }

        Block destinationBlock;
        Location spawnLocation;
        if (clicked.getType() == Material.WATER) {
            destinationBlock = clicked;
            spawnLocation = clicked.getLocation().add(0.5, 0.2, 0.5);
        } else {
            destinationBlock = clicked.getRelative(event.getBlockFace());
            spawnLocation = destinationBlock.getLocation().add(0.5, 0.1, 0.5);
        }

        if (companionDefinition.requiresWaterSpawn()
            && destinationBlock.getType() != Material.WATER) {
            event.getPlayer().sendMessage(
                ChatColor.YELLOW + companionDefinition.displayName() + " must be summoned in water."
            );
            return;
        }

        try {
            Mob companion = service.spawn(event.getPlayer(), companionDefinition, spawnLocation);
            eggState.restoreHealth(item, companion);
            consume(event.getPlayer(), event.getHand(), item);
            event.getPlayer().sendMessage(
                ChatColor.GREEN
                    + companionDefinition.displayName()
                    + " is now following you. Right-click it to manage it."
            );
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to spawn companion", exception);
            event.getPlayer().sendMessage(ChatColor.RED + "The companion could not be summoned here.");
        }
    }

    private Block resolveClickedBlock(
        PlayerInteractEvent event,
        CompanionDefinition definition
    ) {
        if (event.getClickedBlock() != null) {
            return event.getClickedBlock();
        }
        if (!definition.requiresWaterSpawn()) {
            return null;
        }

        RayTraceResult trace = event.getPlayer().rayTraceBlocks(
            OPEN_WATER_RAY_TRACE_DISTANCE,
            FluidCollisionMode.ALWAYS
        );
        if (trace == null || trace.getHitBlock() == null
            || trace.getHitBlock().getType() != Material.WATER) {
            return null;
        }
        return trace.getHitBlock();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseEggOnEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack held = itemInHand(event.getPlayer(), event.getHand());
        if (service.definition(held).isPresent()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                ChatColor.YELLOW + "Use the Companion Egg on a block to summon it."
            );
            return;
        }

        if (!(event.getRightClicked() instanceof Mob companion)
            || !service.isManaged(companion)
            || !service.isOwner(event.getPlayer(), companion)) {
            return;
        }

        event.setCancelled(true);
        uiService.open(event.getPlayer(), companion);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (service.definition(event.getItem()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.getServer().getScheduler().runTask(
            plugin,
            () -> service.teleportFollowers(event.getPlayer())
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null
            && (service.isManaged(event.getTarget()) || service.isCompanionVex(event.getTarget()))
            && service.isSanctuaryDefenseEntity(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Mob mob
            && (service.isManaged(mob) || service.isCompanionVex(mob))) {
            if (!(event.getTarget() instanceof LivingEntity living)
                || !service.targetAllowed(mob, living)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        if ((service.isManaged(attacker) || service.isCompanionVex(attacker))
            && event.getEntity() instanceof LivingEntity victim
            && !service.mayDamage(attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof LivingEntity victim
            && (service.isManaged(victim) || service.isCompanionVex(victim))
            && service.isSanctuaryDefenseEntity(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Player owner
            && !owner.getUniqueId().equals(attacker.getUniqueId())
            && !service.isSanctuaryDefenseEntity(attacker)) {
            service.noteOwnerAttacked(owner, attacker, Instant.now());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && service.isManaged(mob)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            service.ownerId(mob)
                .map(plugin.getServer()::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .ifPresent(player -> player.sendMessage(
                    ChatColor.RED
                        + service.definition(mob)
                            .map(CompanionDefinition::displayName)
                            .orElse("Your companion")
                        + " was killed."
                ));
            service.removeCompanion(mob);
            return;
        }

        if (!(event.getEntity() instanceof Warden warden)
            || service.isSanctuaryDefenseEntity(warden)
            || warden.getKiller() == null
            || ThreadLocalRandom.current().nextDouble() >= WARDEN_EGG_DROP_CHANCE) {
            return;
        }

        CompanionDefinition.byItemId(ExtendedItemIds.COMPANION_WARDEN)
            .map(eggState::createBaseEgg)
            .ifPresent(event.getDrops()::add);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onExplode(EntityExplodeEvent event) {
        if (service.isManaged(event.getEntity()) || service.isCompanionVex(event.getEntity())) {
            event.blockList().clear();
            return;
        }
        if (event.getEntity() instanceof Projectile projectile
            && projectile.getShooter() instanceof Entity shooter
            && (service.isManaged(shooter) || service.isCompanionVex(shooter))) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!service.isManaged(event.getEntity())) {
            return;
        }
        if (event.getEntity().getType() == EntityType.WITHER
            || event.getEntity() instanceof Enderman) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTransform(EntityTransformEvent event) {
        if (service.isManaged(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(EntityTeleportEvent event) {
        if (service.isManaged(event.getEntity()) && !service.consumeAuthorizedTeleport(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEndermanEscape(EndermanEscapeEvent event) {
        if (!service.isManaged(event.getEntity())) {
            return;
        }
        if (event.getReason() == EndermanEscapeEvent.Reason.RUNAWAY
            || service.authorizedTarget(event.getEntity()).isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWardenAnger(WardenAngerChangeEvent event) {
        if (service.isManaged(event.getEntity())
            && (event.getTarget() == null || !service.targetAllowed(event.getEntity(), event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !service.isManaged(mob)) {
            return;
        }
        if (event.getModifiedType() == PotionEffectType.WITHER
            && event.getCause() == EntityPotionEffectEvent.Cause.ATTACK) {
            event.setCancelled(true);
        }
    }

    private static LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof LivingEntity living ? living : null;
        }
        if (damager instanceof EvokerFangs fangs) {
            return fangs.getOwner();
        }
        return null;
    }

    private static ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
    }

    private static void consume(Player player, EquipmentSlot hand, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        int remaining = item.getAmount() - 1;
        if (remaining > 0) {
            item.setAmount(remaining);
            return;
        }
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInOffHand(null);
        }
    }
}

package dev.liamtolkkinen.sanctuary.companion;

import com.destroystokyo.paper.event.entity.EndermanEscapeEvent;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
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
import org.bukkit.entity.Vex;
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

public final class CompanionListener implements Listener {
    private final CompanionService service;
    private final JavaPlugin plugin;
    private final Logger logger;

    public CompanionListener(
        CompanionService service,
        JavaPlugin plugin,
        Logger logger
    ) {
        this.service = service;
        this.plugin = plugin;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseCompanionEgg(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<CompanionDefinition> definition = service.definition(item);
        if (definition.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        CompanionDefinition companionDefinition = definition.orElseThrow();
        Block clicked = event.getClickedBlock();
        Block destinationBlock = clicked.getRelative(event.getBlockFace());
        Location spawnLocation = destinationBlock.getLocation().add(0.5, 0.1, 0.5);
        if (clicked.getType() == Material.WATER) {
            destinationBlock = clicked;
            spawnLocation = clicked.getLocation().add(0.5, 0.2, 0.5);
        }

        if (companionDefinition.requiresWaterSpawn()
            && destinationBlock.getType() != Material.WATER) {
            event.getPlayer().sendMessage(
                ChatColor.YELLOW + companionDefinition.displayName() + " must be summoned in water."
            );
            return;
        }

        try {
            service.spawn(event.getPlayer(), companionDefinition, spawnLocation);
            consume(event.getPlayer(), event.getHand(), item);
            event.getPlayer().sendMessage(
                ChatColor.GREEN
                    + companionDefinition.displayName()
                    + " is now following you. Sneak-right-click it to make it stay."
            );
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to spawn companion", exception);
            event.getPlayer().sendMessage(ChatColor.RED + "The companion could not be summoned here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseEggOnEntity(PlayerInteractEntityEvent event) {
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
        if (!event.getPlayer().isSneaking()) {
            return;
        }

        event.setCancelled(true);
        CompanionMode mode = service.toggleMode(companion);
        String name = service.definition(companion)
            .map(CompanionDefinition::displayName)
            .orElse("Companion");
        if (mode == CompanionMode.STAY) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + name + " will stay here.");
        } else {
            event.getPlayer().sendMessage(ChatColor.GREEN + name + " is following you.");
        }
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
        if (event.getEntity() instanceof Vex vex) {
            service.ensureEvokerVex(vex);
        }

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

        if (attacker instanceof Vex vex) {
            service.ensureEvokerVex(vex);
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
        if (!(event.getEntity() instanceof Mob mob) || !service.isManaged(mob)) {
            return;
        }
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
        if (!service.isManaged(event.getEntity())) {
            return;
        }
        if (!service.teleportDestinationAllowed(event.getEntity(), event.getTo())) {
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
        if (!service.isManaged(event.getEntity())) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity living)
            || !service.targetAllowed(event.getEntity(), living)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWardenDarkness(EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.WARDEN
            || !(event.getEntity() instanceof Player player)
            || event.getNewEffect() == null
            || event.getNewEffect().getType() != PotionEffectType.DARKNESS) {
            return;
        }
        if (service.hasManagedWardenFor(player)) {
            event.setCancelled(true);
        }
    }

    private static LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity living) {
                return living;
            }
        }
        if (damager instanceof EvokerFangs fangs && fangs.getOwner() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static ItemStack itemInHand(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
    }

    private static void consume(Player player, EquipmentSlot hand, ItemStack source) {
        if (player.getGameMode() == GameMode.CREATIVE || source == null) {
            return;
        }
        ItemStack held = itemInHand(player, hand);
        if (held.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }
}

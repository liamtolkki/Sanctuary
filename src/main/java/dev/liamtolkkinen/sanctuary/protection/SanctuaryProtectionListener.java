package dev.liamtolkkinen.sanctuary.protection;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class SanctuaryProtectionListener implements Listener {
    private static final long WARNING_COOLDOWN_NANOS = 1_000_000_000L;

    private final SanctuaryProtectionService protectionService;
    private final Predicate<SanctuaryCapability> protectionEnabled;
    private final Logger logger;
    private final Map<UUID, EnumMap<SanctuaryCapability, Long>> lastWarning = new HashMap<>();

    public SanctuaryProtectionListener(
        SanctuaryProtectionService protectionService,
        Predicate<SanctuaryCapability> protectionEnabled,
        Logger logger
    ) {
        this.protectionService = protectionService;
        this.protectionEnabled = protectionEnabled;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        denyIfNeeded(event.getPlayer(), SanctuaryCapability.BUILD, event.getBlockPlaced().getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // BREAK permission applies to Sanctuary anchors too. If this check allows the
        // break, AnchorBreakListener runs later and handles the anchor lifecycle.
        denyIfNeeded(
            event.getPlayer(),
            SanctuaryCapability.BREAK,
            event.getBlock().getLocation(),
            () -> event.setCancelled(true)
        );
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();

        // Sneak-right-clicking with a block is a placement attempt. BlockPlaceEvent
        // is the authority for BUILD permission, even when placing against an
        // otherwise interactable block such as a door or lever.
        if (event.getPlayer().isSneaking()
            && event.getItem() != null
            && event.getItem().getType().isBlock()) {
            return;
        }

        if (block.getState() instanceof InventoryHolder) {
            // InventoryOpenEvent evaluates the container's actual location.
            return;
        }

        if (isDirectRedstoneControl(block.getType())) {
            denyIfNeeded(
                event.getPlayer(),
                SanctuaryCapability.REDSTONE,
                block.getLocation(),
                () -> event.setCancelled(true)
            );
            return;
        }

        // Ordinary right-clicks are also used by Minecraft to place blocks.
        // Only require INTERACT when the clicked block itself has an interaction.
        if (!block.getType().isInteractable()) {
            return;
        }

        denyIfNeeded(
            event.getPlayer(),
            SanctuaryCapability.INTERACT,
            block.getLocation(),
            () -> event.setCancelled(true)
        );
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null) {
            return;
        }
        denyIfNeeded(player, SanctuaryCapability.CONTAINER, location, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        denyIfNeeded(event.getPlayer(), SanctuaryCapability.ENTITIES, event.getRightClicked().getLocation(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = responsiblePlayer(event.getDamager());
        if (player == null) {
            return;
        }
        denyIfNeeded(player, SanctuaryCapability.ENTITIES, event.getEntity().getLocation(), () -> event.setCancelled(true));
    }

    private void denyIfNeeded(
        Player player,
        SanctuaryCapability capability,
        Location location,
        Runnable cancel
    ) {
        if (!protectionEnabled.test(capability)) {
            return;
        }
        try {
            Optional<Sanctuary> blocking = protectionService.findBlockingSanctuary(
                player.getUniqueId(),
                capability,
                location.getWorld().getName(),
                location.getX(),
                location.getZ()
            );
            if (blocking.isEmpty()) {
                return;
            }
            cancel.run();
            sendWarning(player, capability, blocking.orElseThrow());
        } catch (SQLException exception) {
            cancel.run();
            player.sendMessage(ChatColor.RED + "Sanctuary could not verify permissions. The action was blocked.");
            logger.log(Level.SEVERE, "Failed to evaluate Sanctuary protection at " + location, exception);
        }
    }

    private void sendWarning(Player player, SanctuaryCapability capability, Sanctuary sanctuary) {
        long now = System.nanoTime();
        EnumMap<SanctuaryCapability, Long> warnings = lastWarning.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new EnumMap<>(SanctuaryCapability.class)
        );
        long previous = warnings.getOrDefault(capability, 0L);
        if (now - previous < WARNING_COOLDOWN_NANOS) {
            return;
        }
        warnings.put(capability, now);
        player.sendMessage(ChatColor.RED + denialMessage(capability) + ChatColor.GRAY + " [" + sanctuary.name() + "]");
    }

    private static String denialMessage(SanctuaryCapability capability) {
        return switch (capability) {
            case BUILD -> "You cannot place blocks in this Sanctuary.";
            case BREAK -> "You cannot break blocks in this Sanctuary.";
            case INTERACT -> "You cannot use that in this Sanctuary.";
            case CONTAINER -> "You cannot use containers in this Sanctuary.";
            case REDSTONE -> "You cannot operate redstone controls in this Sanctuary.";
            case ENTITIES -> "You cannot interact with protected entities in this Sanctuary.";
        };
    }

    static boolean isDirectRedstoneControl(Material material) {
        String name = material.name();
        return material == Material.LEVER
            || material == Material.REPEATER
            || material == Material.COMPARATOR
            || material == Material.DAYLIGHT_DETECTOR
            || name.endsWith("_BUTTON");
    }

    private static Player responsiblePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}

package dev.liamtolkkinen.sanctuary.protection;

import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

public final class SanctuaryProtectionListener implements Listener {
    private static final long WARNING_COOLDOWN_NANOS = 1_000_000_000L;

    private final SanctuaryProtectionService protectionService;
    private final Logger logger;
    private final Map<UUID, EnumMap<SanctuaryCapability, Long>> lastWarning = new HashMap<>();

    public SanctuaryProtectionListener(
        SanctuaryProtectionService protectionService,
        Logger logger
    ) {
        this.protectionService = protectionService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        denyIfNeeded(
            event.getPlayer(),
            SanctuaryCapability.BUILD,
            event.getBlockPlaced().getLocation(),
            () -> event.setCancelled(true)
        );
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        /*
         * Sanctuary Beacons are intentionally included here.
         *
         * This listener decides whether the player has BREAK permission at the
         * location. If allowed, the dedicated AnchorBreakListener runs later
         * and handles the Sanctuary-specific lifecycle such as generation,
         * bound Beacon drops, debug deletion, and orphan cleanup.
         */
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

        /*
         * Sneak-right-clicking with a block is a placement attempt. The
         * BlockPlaceEvent is responsible for checking BUILD permission.
         *
         * Without this exception, placing a block against a door, chest,
         * lever, or another interactable block would incorrectly require
         * INTERACT, CONTAINER, or REDSTONE in addition to BUILD.
         */
        if (event.getPlayer().isSneaking()
            && event.getItem() != null
            && event.getItem().getType().isBlock()) {
            return;
        }

        if (block.getState() instanceof InventoryHolder) {
            /*
             * InventoryOpenEvent handles containers using the actual
             * inventory location.
             */
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

        /*
         * Ordinary block right-clicks are also how Minecraft places blocks.
         * Only require INTERACT when the clicked block itself actually has an
         * interaction.
         */
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

        denyIfNeeded(
            player,
            SanctuaryCapability.CONTAINER,
            location,
            () -> event.setCancelled(true)
        );
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        denyIfNeeded(
            event.getPlayer(),
            SanctuaryCapability.ENTITIES,
            event.getRightClicked().getLocation(),
            () -> event.setCancelled(true)
        );
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = resolvePlayerDamager(event.getDamager());
        if (player == null) {
            return;
        }

        denyIfNeeded(
            player,
            SanctuaryCapability.ENTITIES,
            event.getEntity().getLocation(),
            () -> event.setCancelled(true)
        );
    }

    private void denyIfNeeded(
        Player player,
        SanctuaryCapability capability,
        Location location,
        Runnable cancelAction
    ) {
        try {
            var blockingSanctuary = protectionService.findBlockingSanctuary(
                player.getUniqueId(),
                capability,
                location.getWorld().getName(),
                location.getX(),
                location.getZ()
            );

            if (blockingSanctuary.isEmpty()) {
                return;
            }

            cancelAction.run();
            warnPlayer(player, capability);
        } catch (SQLException exception) {
            /*
             * Fail closed. A database failure must not temporarily disable
             * Sanctuary protections.
             */
            cancelAction.run();
            player.sendMessage(
                ChatColor.RED
                    + "Sanctuary could not verify permissions. This action was blocked."
            );
            logger.log(
                Level.SEVERE,
                "Failed to evaluate Sanctuary "
                    + capability
                    + " permission for "
                    + player.getUniqueId(),
                exception
            );
        }
    }

    private void warnPlayer(Player player, SanctuaryCapability capability) {
        long now = System.nanoTime();

        EnumMap<SanctuaryCapability, Long> playerWarnings =
            lastWarning.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new EnumMap<>(SanctuaryCapability.class)
            );

        Long previous = playerWarnings.get(capability);
        if (previous != null && now - previous < WARNING_COOLDOWN_NANOS) {
            return;
        }

        playerWarnings.put(capability, now);

        player.sendMessage(
            ChatColor.RED + denialMessage(capability)
        );
    }

    private static String denialMessage(SanctuaryCapability capability) {
        return switch (capability) {
            case BUILD -> "You cannot place blocks in this Sanctuary.";
            case BREAK -> "You cannot break blocks in this Sanctuary.";
            case INTERACT -> "You cannot interact with that in this Sanctuary.";
            case CONTAINER -> "You cannot use containers in this Sanctuary.";
            case REDSTONE -> "You cannot use redstone controls in this Sanctuary.";
            case ENTITIES -> "You cannot interact with protected entities in this Sanctuary.";
        };
    }

    private static Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (!(damager instanceof Projectile projectile)) {
            return null;
        }

        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player
            ? player
            : null;
    }

    private static boolean isDirectRedstoneControl(Material material) {
        return switch (material) {
            case LEVER,
                 REPEATER,
                 COMPARATOR,
                 DAYLIGHT_DETECTOR -> true;
            default -> material.name().endsWith("_BUTTON");
        };
    }
}

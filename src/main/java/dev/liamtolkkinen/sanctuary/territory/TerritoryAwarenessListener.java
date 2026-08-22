package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.security.SanctuaryRelationship;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TerritoryAwarenessListener implements Listener {
    private final SanctuaryRepository repository;
    private final TerritoryPresenceService presenceService;
    private final SanctuarySecurityService securityService;
    private final BooleanSupplier entryTitleEnabled;
    private final BooleanSupplier exitMessageEnabled;
    private final BooleanSupplier ownerEntryAlertsEnabled;
    private final Logger logger;
    private final Map<UUID, UUID> currentSanctuaryByPlayer = new HashMap<>();

    public TerritoryAwarenessListener(
        SanctuaryRepository repository,
        TerritoryPresenceService presenceService,
        SanctuarySecurityService securityService,
        BooleanSupplier entryTitleEnabled,
        BooleanSupplier exitMessageEnabled,
        BooleanSupplier ownerEntryAlertsEnabled,
        Logger logger
    ) {
        this.repository = repository;
        this.presenceService = presenceService;
        this.securityService = securityService;
        this.entryTitleEnabled = entryTitleEnabled;
        this.exitMessageEnabled = exitMessageEnabled;
        this.ownerEntryAlertsEnabled = ownerEntryAlertsEnabled;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || sameHorizontalBlock(from, to)) {
            return;
        }
        updatePresence(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            updatePresence(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updatePresence(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentSanctuaryByPlayer.remove(event.getPlayer().getUniqueId());
    }

    void updatePresence(Player player, Location location) {
        try {
            Optional<Sanctuary> current = presenceService.findCurrentSanctuary(
                repository.findActiveInWorld(location.getWorld().getName()),
                location.getWorld().getName(),
                location.getX(),
                location.getZ()
            );

            UUID playerId = player.getUniqueId();
            UUID previousId = currentSanctuaryByPlayer.get(playerId);
            UUID currentId = current.map(Sanctuary::id).orElse(null);
            if (java.util.Objects.equals(previousId, currentId)) {
                return;
            }

            if (previousId != null) {
                repository.findById(previousId).ifPresent(previous -> handleExit(player, previous));
            }

            if (current.isPresent()) {
                Sanctuary entered = current.orElseThrow();
                currentSanctuaryByPlayer.put(playerId, entered.id());
                handleEnter(player, entered);
            } else {
                currentSanctuaryByPlayer.remove(playerId);
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to update Sanctuary territory presence for " + player.getName(), exception);
        }
    }

    private void handleEnter(Player player, Sanctuary sanctuary) throws SQLException {
        SanctuaryRelationship relationship = securityService.relationship(sanctuary, player.getUniqueId());
        SanctuarySecurityMode mode = securityService.mode(sanctuary);

        if (entryTitleEnabled.getAsBoolean()) {
            player.sendTitle(
                ChatColor.GOLD + sanctuary.name(),
                entrySubtitle(relationship, mode),
                10,
                50,
                10
            );
        }

        if (relationship == SanctuaryRelationship.BLACKLISTED) {
            player.sendActionBar(
                Component.text("Restricted Sanctuary - you are blacklisted.", NamedTextColor.RED)
            );
        } else if (relationship == SanctuaryRelationship.NEUTRAL && mode == SanctuarySecurityMode.LOCKDOWN) {
            player.sendActionBar(
                Component.text(
                    "LOCKDOWN - you do not have permission to enter this Sanctuary.",
                    NamedTextColor.RED
                )
            );
        }

        if (sanctuary.debugEphemeral()) {
            player.sendMessage(
                ChatColor.LIGHT_PURPLE
                    + "[Sanctuary Debug] "
                    + ChatColor.WHITE
                    + player.getName()
                    + " entered debug Sanctuary "
                    + sanctuary.id()
                    + "."
            );
        }

        if (ownerEntryAlertsEnabled.getAsBoolean() && !sanctuary.ownerId().equals(player.getUniqueId())) {
            Player owner = Bukkit.getPlayer(sanctuary.ownerId());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(
                    ChatColor.GOLD
                        + "[Sanctuary] "
                        + ChatColor.WHITE
                        + player.getName()
                        + ChatColor.GRAY
                        + " entered "
                        + sanctuary.name()
                        + "."
                );
            }
        }
    }

    static String entrySubtitle(
        SanctuaryRelationship relationship,
        SanctuarySecurityMode mode
    ) {
        return switch (relationship) {
            case OWNER -> ChatColor.AQUA + "Your Sanctuary";
            case TRUSTED -> ChatColor.GREEN + "Trusted Territory";
            case BLACKLISTED -> ChatColor.RED + "Restricted - Blacklisted";
            case NEUTRAL -> mode == SanctuarySecurityMode.LOCKDOWN
                ? ChatColor.RED + "LOCKDOWN - Unauthorized"
                : ChatColor.WHITE + "Neutral Territory";
        };
    }

    private void handleExit(Player player, Sanctuary sanctuary) {
        if (exitMessageEnabled.getAsBoolean()) {
            player.sendMessage(
                ChatColor.GRAY + "You left " + ChatColor.GOLD + sanctuary.name() + ChatColor.GRAY + "."
            );
        }
    }

    private static boolean sameHorizontalBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld())
            && first.getBlockX() == second.getBlockX()
            && first.getBlockZ() == second.getBlockZ();
    }
}

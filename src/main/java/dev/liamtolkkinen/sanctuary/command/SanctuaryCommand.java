package dev.liamtolkkinen.sanctuary.command;

import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorLifecycleService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorRecoveryException;
import dev.liamtolkkinen.sanctuary.anchor.AnchorRecoveryResult;
import dev.liamtolkkinen.sanctuary.anchor.DebugBeaconRegistrationService;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.territory.TerritoryBoundaryService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustEntry;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SanctuaryCommand implements CommandExecutor, TabCompleter {
    private final SanctuaryPlugin plugin;
    private final AnchorItemService anchorItemService;
    private final AnchorLifecycleService lifecycleService;
    private final DebugBeaconRegistrationService debugBeaconService;
    private final TerritoryBoundaryService boundaryService;
    private final SanctuaryRepository repository;
    private final SanctuaryPermissionService permissionService;

    public SanctuaryCommand(
        SanctuaryPlugin plugin,
        AnchorItemService anchorItemService,
        AnchorLifecycleService lifecycleService,
        DebugBeaconRegistrationService debugBeaconService,
        TerritoryBoundaryService boundaryService,
        SanctuaryRepository repository,
        SanctuaryPermissionService permissionService
    ) {
        this.plugin = plugin;
        this.anchorItemService = anchorItemService;
        this.lifecycleService = lifecycleService;
        this.debugBeaconService = debugBeaconService;
        this.boundaryService = boundaryService;
        this.repository = repository;
        this.permissionService = permissionService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "Sanctuary " + ChatColor.GRAY + plugin.getPluginMeta().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Database: " + ChatColor.GREEN + "ready");
            sender.sendMessage(ChatColor.GRAY + "Beacon lifecycle, territory, awareness, trust, and capabilities are active.");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("boundary")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only a player can display Sanctuary boundaries.");
                return true;
            }
            return showBoundary(player, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recover")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only a player can recover a Sanctuary Beacon.");
                return true;
            }
            return recoverBeacon(player, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("trust") && args[1].equalsIgnoreCase("list")) {
            return printTrustList(sender, args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("trust")) {
            return setTrusted(sender, args[1], args[2], true);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("untrust")) {
            return setTrusted(sender, args[1], args[2], false);
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("capability")) {
            return setCapability(sender, args[1], args[2], args[3], args[4]);
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("sanctuary.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                return true;
            }

            if (args.length == 2 && args[1].equalsIgnoreCase("reload")) {
                try {
                    plugin.reloadSanctuaryConfig();
                    sender.sendMessage(ChatColor.GREEN + "Sanctuary configuration reloaded.");
                } catch (IllegalStateException exception) {
                    sender.sendMessage(ChatColor.RED + "Sanctuary configuration is invalid: " + exception.getMessage());
                }
                return true;
            }

            if (args.length == 2 && args[1].equalsIgnoreCase("beacons")) {
                return printRegisteredBeacons(sender);
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("permissions")) {
                return printPermissions(sender, args[2], args[3]);
            }

            if (args.length == 2 && args[1].equalsIgnoreCase("debugbeacon")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Console must specify a target player: /sanctuary admin debugbeacon <player>");
                    return true;
                }
                return giveDebugBeacon(sender, player);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("debugbeacon")) {
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "That player is not online.");
                    return true;
                }
                return giveDebugBeacon(sender, target);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("givebeacon")) {
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "That player is not online.");
                    return true;
                }
                giveOrDrop(target, anchorItemService.createUnboundBeacon());
                sender.sendMessage(ChatColor.GREEN + "Gave an unbound Sanctuary Beacon to " + target.getName() + ".");
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.GOLD + "You received an unbound Sanctuary Beacon.");
                }
                return true;
            }
        }

        sender.sendMessage(ChatColor.YELLOW
            + "Usage: /sanctuary [status|boundary <name|all>|recover <id>|trust <sanctuary> <player>|"
            + "trust list <sanctuary>|untrust <sanctuary> <player>|capability <sanctuary> <player> "
            + "<capability> <allow|deny>|admin ...]");
        return true;
    }

    private boolean setTrusted(CommandSender sender, String sanctuarySelector, String playerSelector, boolean trusted) {
        try {
            Sanctuary sanctuary = resolveManagedSanctuary(sender, sanctuarySelector, false).orElse(null);
            if (sanctuary == null) {
                sender.sendMessage(ChatColor.RED + "No manageable Sanctuary matches '" + sanctuarySelector + "'.");
                return true;
            }
            UUID playerId = resolvePlayerId(playerSelector).orElse(null);
            if (playerId == null) {
                sender.sendMessage(ChatColor.RED + "Player must be online, previously seen by the server, or supplied as a UUID.");
                return true;
            }
            if (trusted) {
                permissionService.trust(sanctuary, playerId, Instant.now());
                sender.sendMessage(ChatColor.GREEN + playerLabel(playerId) + " is now trusted by " + sanctuary.name() + ".");
                sender.sendMessage(ChatColor.GRAY + "No capabilities are granted automatically. Grant them explicitly with /sanctuary capability.");
            } else {
                permissionService.untrust(sanctuary, playerId);
                sender.sendMessage(ChatColor.GREEN + playerLabel(playerId) + " is no longer trusted by " + sanctuary.name() + ".");
                sender.sendMessage(ChatColor.GRAY + "All of that player's Sanctuary capability grants were removed.");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(ChatColor.RED + exception.getMessage());
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not update trust.");
            plugin.getLogger().log(Level.SEVERE, "Failed to update Sanctuary trust", exception);
        }
        return true;
    }

    private boolean setCapability(
        CommandSender sender,
        String sanctuarySelector,
        String playerSelector,
        String capabilityText,
        String action
    ) {
        try {
            Sanctuary sanctuary = resolveManagedSanctuary(sender, sanctuarySelector, false).orElse(null);
            if (sanctuary == null) {
                sender.sendMessage(ChatColor.RED + "No manageable Sanctuary matches '" + sanctuarySelector + "'.");
                return true;
            }
            UUID playerId = resolvePlayerId(playerSelector).orElse(null);
            if (playerId == null) {
                sender.sendMessage(ChatColor.RED + "Player must be online, previously seen by the server, or supplied as a UUID.");
                return true;
            }
            SanctuaryCapability capability;
            try {
                capability = SanctuaryCapability.parse(capabilityText);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(ChatColor.RED + "Unknown capability. Use: " + capabilityNames());
                return true;
            }
            boolean allowed;
            if (action.equalsIgnoreCase("allow")) {
                allowed = true;
            } else if (action.equalsIgnoreCase("deny")) {
                allowed = false;
            } else {
                sender.sendMessage(ChatColor.RED + "Capability action must be allow or deny.");
                return true;
            }

            permissionService.setCapability(sanctuary, playerId, capability, allowed);
            sender.sendMessage((allowed ? ChatColor.GREEN + "Granted " : ChatColor.YELLOW + "Revoked ")
                + capability.name() + " for " + playerLabel(playerId) + " in " + sanctuary.name() + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(ChatColor.RED + exception.getMessage());
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not update that capability.");
            plugin.getLogger().log(Level.SEVERE, "Failed to update Sanctuary capability", exception);
        }
        return true;
    }

    private boolean printTrustList(CommandSender sender, String sanctuarySelector) {
        try {
            Sanctuary sanctuary = resolveManagedSanctuary(sender, sanctuarySelector, false).orElse(null);
            if (sanctuary == null) {
                sender.sendMessage(ChatColor.RED + "No manageable Sanctuary matches '" + sanctuarySelector + "'.");
                return true;
            }
            List<SanctuaryTrustEntry> entries = permissionService.trustedPlayers(sanctuary);
            sender.sendMessage(ChatColor.GOLD + "Trust for " + sanctuary.name() + ChatColor.GRAY + " (" + entries.size() + ")");
            sender.sendMessage(ChatColor.YELLOW + "Owner: " + playerLabel(sanctuary.ownerId()) + ChatColor.GRAY + " [ALL]");
            if (entries.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No additional players are trusted.");
            }
            for (SanctuaryTrustEntry entry : entries) {
                sender.sendMessage(ChatColor.YELLOW + playerLabel(entry.playerId()) + ChatColor.GRAY
                    + " " + entry.playerId() + " capabilities=" + formatCapabilities(entry.capabilities()));
            }
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not read trust data.");
            plugin.getLogger().log(Level.SEVERE, "Failed to list Sanctuary trust", exception);
        }
        return true;
    }

    private boolean printPermissions(CommandSender sender, String sanctuarySelector, String playerSelector) {
        try {
            Sanctuary sanctuary = resolveManagedSanctuary(sender, sanctuarySelector, true).orElse(null);
            if (sanctuary == null) {
                sender.sendMessage(ChatColor.RED + "No Sanctuary matches '" + sanctuarySelector + "'.");
                return true;
            }
            UUID playerId = resolvePlayerId(playerSelector).orElse(null);
            if (playerId == null) {
                sender.sendMessage(ChatColor.RED + "Player must be previously seen by the server or supplied as a UUID.");
                return true;
            }
            boolean owner = sanctuary.ownerId().equals(playerId);
            boolean trusted = permissionService.isTrusted(sanctuary, playerId);
            Set<SanctuaryCapability> effective = permissionService.effectiveCapabilities(sanctuary, playerId);

            sender.sendMessage(ChatColor.GOLD + "Sanctuary permissions");
            sender.sendMessage(ChatColor.GRAY + "Sanctuary: " + ChatColor.WHITE + sanctuary.name() + ChatColor.DARK_GRAY + " (" + sanctuary.id() + ")");
            sender.sendMessage(ChatColor.GRAY + "Player: " + ChatColor.WHITE + playerLabel(playerId) + ChatColor.DARK_GRAY + " (" + playerId + ")");
            sender.sendMessage(ChatColor.GRAY + "Owner: " + (owner ? ChatColor.GREEN + "yes" : ChatColor.RED + "no")
                + ChatColor.GRAY + "  Trusted: " + (trusted ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
            for (SanctuaryCapability capability : SanctuaryCapability.values()) {
                boolean allowed = effective.contains(capability);
                sender.sendMessage(ChatColor.GRAY + "  " + capability.name() + " "
                    + (allowed ? ChatColor.GREEN + "ALLOWED" : ChatColor.RED + "DENIED"));
            }
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not evaluate permissions.");
            plugin.getLogger().log(Level.SEVERE, "Failed to inspect Sanctuary permissions", exception);
        }
        return true;
    }

    private Optional<Sanctuary> resolveManagedSanctuary(
        CommandSender sender,
        String selector,
        boolean includeDestroyed
    ) throws SQLException {
        List<Sanctuary> candidates = repository.findAll().stream()
            .filter(value -> !value.debugEphemeral())
            .filter(value -> includeDestroyed || value.state() != SanctuaryState.DESTROYED)
            .filter(value -> sender.hasPermission("sanctuary.admin")
                || (sender instanceof Player player && value.ownerId().equals(player.getUniqueId())))
            .toList();
        return resolveSanctuarySelector(selector, candidates);
    }

    private static Optional<UUID> resolvePlayerId(String selector) {
        try {
            return Optional.of(UUID.fromString(selector));
        } catch (IllegalArgumentException ignored) {
            Player online = Bukkit.getPlayerExact(selector);
            if (online != null) {
                return Optional.of(online.getUniqueId());
            }
            return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> player.getName() != null)
                .filter(player -> player.getName().equalsIgnoreCase(selector))
                .map(OfflinePlayer::getUniqueId)
                .findFirst();
        }
    }

    private static String playerLabel(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank() ? playerId.toString() : name;
    }

    private boolean showBoundary(Player player, String selector) {
        try {
            List<Sanctuary> visible = boundaryCandidates(player);
            if (selector.equalsIgnoreCase("all")) {
                boundaryService.showAll(player, visible, plugin.getBoundaryParticleSpacing(), plugin.getBoundaryDisplaySeconds(), plugin.getBoundaryMaximumRenderDistance());
                player.sendMessage(ChatColor.GREEN + "Displaying nearby Sanctuary boundaries" + ChatColor.GRAY
                    + " within " + plugin.getBoundaryMaximumRenderDistance() + " blocks of their boundary.");
                return true;
            }
            Optional<Sanctuary> result = resolveSanctuarySelector(selector, visible);
            if (result.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No active Sanctuary matches '" + selector + "'.");
                return true;
            }
            Sanctuary sanctuary = result.orElseThrow();
            boundaryService.show(player, sanctuary, plugin.getBoundaryParticleSpacing(), plugin.getBoundaryDisplaySeconds());
            player.sendMessage(ChatColor.GREEN + "Displaying the boundary for " + sanctuary.name()
                + ChatColor.GRAY + " for " + plugin.getBoundaryDisplaySeconds() + " seconds.");
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not read the territory registry.");
            plugin.getLogger().log(Level.SEVERE, "Failed to show Sanctuary boundary", exception);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
        return true;
    }

    private List<Sanctuary> boundaryCandidates(Player player) throws SQLException {
        return repository.findAll().stream()
            .filter(value -> value.type() == SanctuaryType.BEACON)
            .filter(value -> value.state() == SanctuaryState.ACTIVE)
            .filter(value -> value.position().isPresent())
            .filter(value -> value.ownerId().equals(player.getUniqueId()) || player.hasPermission("sanctuary.admin"))
            .toList();
    }

    private static Optional<Sanctuary> resolveSanctuarySelector(String selector, List<Sanctuary> candidates) {
        try {
            UUID id = UUID.fromString(selector);
            return candidates.stream().filter(value -> value.id().equals(id)).findFirst();
        } catch (IllegalArgumentException ignored) {
            return candidates.stream()
                .filter(value -> sanctuaryLabel(value, candidates).equalsIgnoreCase(selector))
                .findFirst();
        }
    }

    private static String sanctuaryLabel(Sanctuary sanctuary, List<Sanctuary> candidates) {
        String base = sanctuary.name().trim().replaceAll("\\s+", "_");
        long sameName = candidates.stream().filter(value -> value.name().equalsIgnoreCase(sanctuary.name())).count();
        if (sameName == 1) {
            return base;
        }
        String ownerName = Bukkit.getOfflinePlayer(sanctuary.ownerId()).getName();
        if (ownerName != null && !ownerName.isBlank()) {
            String ownerLabel = base + "@" + ownerName;
            long sameOwnerLabel = candidates.stream()
                .filter(value -> value.name().equalsIgnoreCase(sanctuary.name()))
                .filter(value -> value.ownerId().equals(sanctuary.ownerId()))
                .count();
            if (sameOwnerLabel == 1) {
                return ownerLabel;
            }
        }
        return base + "~" + sanctuary.id().toString().substring(0, 8);
    }

    private boolean recoverBeacon(Player player, String idText) {
        if (!plugin.isAnchorRecoveryEnabled()) {
            player.sendMessage(ChatColor.RED + "Sanctuary Beacon recovery is disabled.");
            return true;
        }
        UUID sanctuaryId;
        try {
            sanctuaryId = UUID.fromString(idText);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + "That is not a valid Sanctuary ID.");
            return true;
        }
        try {
            AnchorRecoveryResult result = lifecycleService.recover(sanctuaryId, player.getUniqueId(), Duration.ofSeconds(plugin.getAnchorRecoveryCooldownSeconds()));
            giveOrDrop(player, anchorItemService.createBoundBeacon(result.metadata()));
            player.sendMessage(ChatColor.GREEN + "Recovered the Beacon for " + result.sanctuary().name()
                + ChatColor.GRAY + " (generation " + result.metadata().generation() + ").");
            player.sendMessage(ChatColor.YELLOW + "Any older copy of this Sanctuary Beacon is now permanently stale.");
        } catch (AnchorRecoveryException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not recover this Beacon.");
            plugin.getLogger().log(Level.SEVERE, "Failed to recover Sanctuary Beacon " + sanctuaryId, exception);
        }
        return true;
    }

    private boolean giveDebugBeacon(CommandSender sender, Player target) {
        Sanctuary sanctuary = null;
        try {
            sanctuary = debugBeaconService.register(plugin.getInitialTerritoryRadius());
            ItemStack item = anchorItemService.createBoundBeacon(sanctuary);
            giveOrDrop(target, item);
            sender.sendMessage(ChatColor.GREEN + "Registered ephemeral debug Beacon " + sanctuary.id()
                + " with synthetic owner " + sanctuary.ownerId() + " and gave it to " + target.getName() + ".");
            sender.sendMessage(ChatColor.YELLOW + "Breaking this debug Beacon deletes its Sanctuary record and drops nothing.");
            if (!target.equals(sender)) {
                target.sendMessage(ChatColor.GOLD + "You received an ephemeral debug Sanctuary Beacon. It represents another owner and is deleted when broken.");
            }
            return true;
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not register a debug Beacon.");
            plugin.getLogger().log(Level.SEVERE, "Failed to register debug Sanctuary Beacon", exception);
            return true;
        } catch (RuntimeException exception) {
            if (sanctuary != null) {
                try {
                    debugBeaconService.remove(sanctuary.id());
                } catch (SQLException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            sender.sendMessage(ChatColor.RED + "Sanctuary could not create the debug Beacon item.");
            plugin.getLogger().log(Level.SEVERE, "Failed to create debug Sanctuary Beacon", exception);
            return true;
        }
    }

    private boolean printRegisteredBeacons(CommandSender sender) {
        try {
            List<Sanctuary> beacons = repository.findAll().stream().filter(value -> value.type() == SanctuaryType.BEACON).toList();
            sender.sendMessage(ChatColor.GOLD + "Registered Sanctuary Beacons: " + ChatColor.WHITE + beacons.size());
            if (beacons.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No Sanctuary Beacons are registered.");
                return true;
            }
            for (Sanctuary sanctuary : beacons) {
                sender.sendMessage(ChatColor.YELLOW + sanctuary.name() + ChatColor.GRAY + " [" + sanctuary.state()
                    + (sanctuary.debugEphemeral() ? ", DEBUG-EPHEMERAL" : "") + "]");
                sender.sendMessage(ChatColor.GRAY + "  id=" + sanctuary.id() + " owner=" + sanctuary.ownerId());
                sender.sendMessage(ChatColor.GRAY + "  tier=" + sanctuary.tier() + " generation=" + sanctuary.anchorGeneration()
                    + " radius=" + String.format(Locale.ROOT, "%.2f", sanctuary.territoryRadius()));
                sender.sendMessage(ChatColor.GRAY + "  location=" + formatPosition(sanctuary.position()));
                sender.sendMessage(ChatColor.GRAY + "  created=" + sanctuary.createdAt() + " updated=" + sanctuary.updatedAt());
                if (sanctuary.destroyedAt().isPresent()) {
                    sender.sendMessage(ChatColor.RED + "  destroyed=" + sanctuary.destroyedAt().orElseThrow()
                        + " reason=" + sanctuary.destructionReason().orElse("unknown"));
                }
            }
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not read the Beacon registry.");
            plugin.getLogger().log(Level.SEVERE, "Failed to list registered Sanctuary Beacons", exception);
        }
        return true;
    }

    private static String formatPosition(Optional<SanctuaryPosition> position) {
        if (position.isEmpty()) {
            return "none";
        }
        SanctuaryPosition value = position.orElseThrow();
        return value.world() + " " + value.x() + " " + value.y() + " " + value.z();
    }

    private static void giveOrDrop(Player target, ItemStack item) {
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("status", "boundary", "recover", "trust", "untrust", "capability"));
            if (sender.hasPermission("sanctuary.admin")) {
                values.add("admin");
            }
            return filter(values, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("boundary") && sender instanceof Player player) {
            try {
                List<Sanctuary> candidates = boundaryCandidates(player);
                List<String> values = new ArrayList<>();
                values.add("all");
                values.addAll(candidates.stream().map(value -> sanctuaryLabel(value, candidates)).sorted(String.CASE_INSENSITIVE_ORDER).toList());
                return filter(values, args[1]);
            } catch (SQLException exception) {
                return List.of();
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recover") && sender instanceof Player player) {
            try {
                return filter(repository.findByOwner(player.getUniqueId()).stream()
                    .filter(SanctuaryCommand::isRecoverableAutocompleteCandidate)
                    .map(value -> value.id().toString()).toList(), args[1]);
            } catch (SQLException exception) {
                return List.of();
            }
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("capability"))) {
            try {
                List<Sanctuary> candidates = manageableCandidates(sender, false);
                List<String> values = new ArrayList<>();
                if (args[0].equalsIgnoreCase("trust")) {
                    values.add("list");
                }
                values.addAll(candidates.stream().map(value -> sanctuaryLabel(value, candidates)).toList());
                return filter(values, args[1]);
            } catch (SQLException exception) {
                return List.of();
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("trust") && args[1].equalsIgnoreCase("list")) {
            try {
                List<Sanctuary> candidates = manageableCandidates(sender, false);
                return filter(candidates.stream().map(value -> sanctuaryLabel(value, candidates)).toList(), args[2]);
            } catch (SQLException exception) {
                return List.of();
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("capability"))) {
            return filter(knownPlayerNames(), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("capability")) {
            return filter(Arrays.stream(SanctuaryCapability.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList(), args[3]);
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("capability")) {
            return filter(List.of("allow", "deny"), args[4]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("sanctuary.admin")) {
            return filter(List.of("reload", "beacons", "givebeacon", "debugbeacon", "permissions"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("permissions") && sender.hasPermission("sanctuary.admin")) {
            try {
                List<Sanctuary> candidates = manageableCandidates(sender, true);
                return filter(candidates.stream().map(value -> sanctuaryLabel(value, candidates)).toList(), args[2]);
            } catch (SQLException exception) {
                return List.of();
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("permissions") && sender.hasPermission("sanctuary.admin")) {
            return filter(knownPlayerNames(), args[3]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")
            && (args[1].equalsIgnoreCase("givebeacon") || args[1].equalsIgnoreCase("debugbeacon"))
            && sender.hasPermission("sanctuary.admin")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(), args[2]);
        }

        return List.of();
    }

    private List<Sanctuary> manageableCandidates(CommandSender sender, boolean includeDestroyed) throws SQLException {
        return repository.findAll().stream()
            .filter(value -> !value.debugEphemeral())
            .filter(value -> includeDestroyed || value.state() != SanctuaryState.DESTROYED)
            .filter(value -> sender.hasPermission("sanctuary.admin")
                || (sender instanceof Player player && value.ownerId().equals(player.getUniqueId())))
            .toList();
    }

    private static List<String> knownPlayerNames() {
        return Arrays.stream(Bukkit.getOfflinePlayers())
            .map(OfflinePlayer::getName)
            .filter(name -> name != null && !name.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    static boolean isRecoverableAutocompleteCandidate(Sanctuary sanctuary) {
        return sanctuary.type() == SanctuaryType.BEACON
            && sanctuary.state() == SanctuaryState.INACTIVE
            && !sanctuary.debugEphemeral();
    }

    private static String capabilityNames() {
        return String.join(", ", Arrays.stream(SanctuaryCapability.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList());
    }

    private static String formatCapabilities(Set<SanctuaryCapability> capabilities) {
        if (capabilities.isEmpty()) {
            return "none";
        }
        return String.join(",", capabilities.stream().map(Enum::name).sorted().toList());
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}

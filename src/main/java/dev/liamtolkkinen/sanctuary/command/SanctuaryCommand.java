package dev.liamtolkkinen.sanctuary.command;

import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorLifecycleService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorRecoveryException;
import dev.liamtolkkinen.sanctuary.anchor.AnchorRecoveryResult;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    private final SanctuaryRepository repository;

    public SanctuaryCommand(
        SanctuaryPlugin plugin,
        AnchorItemService anchorItemService,
        AnchorLifecycleService lifecycleService,
        SanctuaryRepository repository
    ) {
        this.plugin = plugin;
        this.anchorItemService = anchorItemService;
        this.lifecycleService = lifecycleService;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(
                ChatColor.GOLD
                    + "Sanctuary "
                    + ChatColor.GRAY
                    + plugin.getPluginMeta().getVersion()
            );
            sender.sendMessage(ChatColor.GRAY + "Database: " + ChatColor.GREEN + "ready");
            sender.sendMessage(
                ChatColor.GRAY + "Complete Beacon anchor lifecycle support is active."
            );
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recover")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only a player can recover a Sanctuary Beacon.");
                return true;
            }
            return recoverBeacon(player, args[1]);
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
                    sender.sendMessage(
                        ChatColor.RED + "Sanctuary configuration is invalid: " + exception.getMessage()
                    );
                }
                return true;
            }

            if (args.length == 2 && args[1].equalsIgnoreCase("beacons")) {
                return printRegisteredBeacons(sender);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("givebeacon")) {
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "That player is not online.");
                    return true;
                }

                giveOrDrop(target, anchorItemService.createUnboundBeacon());

                sender.sendMessage(
                    ChatColor.GREEN + "Gave an unbound Sanctuary Beacon to " + target.getName() + "."
                );
                if (!target.equals(sender)) {
                    target.sendMessage(ChatColor.GOLD + "You received an unbound Sanctuary Beacon.");
                }
                return true;
            }
        }

        sender.sendMessage(
            ChatColor.YELLOW
                + "Usage: /sanctuary [status|recover <sanctuary-id>|admin reload|"
                + "admin beacons|admin givebeacon <player>]"
        );
        return true;
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
            AnchorRecoveryResult result = lifecycleService.recover(
                sanctuaryId,
                player.getUniqueId(),
                Duration.ofSeconds(plugin.getAnchorRecoveryCooldownSeconds())
            );
            giveOrDrop(player, anchorItemService.createBoundBeacon(result.metadata()));
            player.sendMessage(
                ChatColor.GREEN
                    + "Recovered the Beacon for "
                    + result.sanctuary().name()
                    + ChatColor.GRAY
                    + " (generation "
                    + result.metadata().generation()
                    + ")."
            );
            player.sendMessage(
                ChatColor.YELLOW
                    + "Any older copy of this Sanctuary Beacon is now permanently stale."
            );
        } catch (AnchorRecoveryException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not recover this Beacon.");
            plugin.getLogger().log(
                Level.SEVERE,
                "Failed to recover Sanctuary Beacon " + sanctuaryId,
                exception
            );
        }
        return true;
    }

    private boolean printRegisteredBeacons(CommandSender sender) {
        try {
            List<Sanctuary> beacons = repository.findAll().stream()
                .filter(value -> value.type() == SanctuaryType.BEACON)
                .toList();

            sender.sendMessage(
                ChatColor.GOLD
                    + "Registered Sanctuary Beacons: "
                    + ChatColor.WHITE
                    + beacons.size()
            );
            if (beacons.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No Sanctuary Beacons are registered.");
                return true;
            }

            for (Sanctuary sanctuary : beacons) {
                sender.sendMessage(
                    ChatColor.YELLOW
                        + sanctuary.name()
                        + ChatColor.GRAY
                        + " ["
                        + sanctuary.state()
                        + "]"
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "  id="
                        + sanctuary.id()
                        + " owner="
                        + sanctuary.ownerId()
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "  tier="
                        + sanctuary.tier()
                        + " generation="
                        + sanctuary.anchorGeneration()
                        + " territoryArea="
                        + sanctuary.territoryArea()
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "  location="
                        + formatPosition(sanctuary.position())
                );
                sender.sendMessage(
                    ChatColor.GRAY
                        + "  created="
                        + sanctuary.createdAt()
                        + " updated="
                        + sanctuary.updatedAt()
                );
                if (sanctuary.destroyedAt().isPresent()) {
                    sender.sendMessage(
                        ChatColor.RED
                            + "  destroyed="
                            + sanctuary.destroyedAt().orElseThrow()
                            + " reason="
                            + sanctuary.destructionReason().orElse("unknown")
                    );
                }
            }
            return true;
        } catch (SQLException exception) {
            sender.sendMessage(ChatColor.RED + "Sanctuary could not read the Beacon registry.");
            plugin.getLogger().log(Level.SEVERE, "Failed to list registered Sanctuary Beacons", exception);
            return true;
        }
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
        leftovers.values().forEach(
            leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover)
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("status", "recover"));
            if (sender.hasPermission("sanctuary.admin")) {
                values.add("admin");
            }
            return filter(values, args[0]);
        }

        if (
            args.length == 2
                && args[0].equalsIgnoreCase("recover")
                && sender instanceof Player player
        ) {
            try {
                return filter(
                    repository.findByOwner(player.getUniqueId()).stream()
                        .filter(value -> value.type() == SanctuaryType.BEACON)
                        .map(value -> value.id().toString())
                        .toList(),
                    args[1]
                );
            } catch (SQLException exception) {
                return List.of();
            }
        }

        if (
            args.length == 2
                && args[0].equalsIgnoreCase("admin")
                && sender.hasPermission("sanctuary.admin")
        ) {
            return filter(List.of("reload", "beacons", "givebeacon"), args[1]);
        }

        if (
            args.length == 3
                && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("givebeacon")
                && sender.hasPermission("sanctuary.admin")
        ) {
            return filter(
                Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList(),
                args[2]
            );
        }

        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
            .toList();
    }
}

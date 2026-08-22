package dev.liamtolkkinen.sanctuary.command;

import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public SanctuaryCommand(
        SanctuaryPlugin plugin,
        AnchorItemService anchorItemService
    ) {
        this.plugin = plugin;
        this.anchorItemService = anchorItemService;
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
                ChatColor.GRAY + "Anchor identity and first-placement phase is active."
            );
            return true;
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

            if (args.length == 3 && args[1].equalsIgnoreCase("givebeacon")) {
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "That player is not online.");
                    return true;
                }

                ItemStack beacon = anchorItemService.createUnboundBeacon();
                Map<Integer, ItemStack> leftovers = target.getInventory().addItem(beacon);
                leftovers.values().forEach(
                    item -> target.getWorld().dropItemNaturally(target.getLocation(), item)
                );

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
                + "Usage: /sanctuary [status|admin reload|admin givebeacon <player>]"
        );
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("sanctuary.admin")) {
                values.add("admin");
            }
            return filter(values, args[0]);
        }

        if (
            args.length == 2
                && args[0].equalsIgnoreCase("admin")
                && sender.hasPermission("sanctuary.admin")
        ) {
            return filter(List.of("reload", "givebeacon"), args[1]);
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

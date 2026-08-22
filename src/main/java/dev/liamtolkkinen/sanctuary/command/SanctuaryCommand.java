package dev.liamtolkkinen.sanctuary.command;

import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SanctuaryCommand implements CommandExecutor, TabCompleter {
    private final SanctuaryPlugin plugin;

    public SanctuaryCommand(SanctuaryPlugin plugin) {
        this.plugin = plugin;
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
            sender.sendMessage(ChatColor.GRAY + "Foundation/persistence phase is active.");
            return true;
        }

        if (
            args.length == 2
                && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("reload")
        ) {
            if (!sender.hasPermission("sanctuary.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                return true;
            }

            plugin.reloadSanctuaryConfig();
            sender.sendMessage(ChatColor.GREEN + "Sanctuary configuration reloaded.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /sanctuary [status|admin reload]");
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
            return filter(List.of("reload"), args[1]);
        }

        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(java.util.Locale.ROOT);
        return values.stream()
            .filter(value -> value.startsWith(normalized))
            .toList();
    }
}

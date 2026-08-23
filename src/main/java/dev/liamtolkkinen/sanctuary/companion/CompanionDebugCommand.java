package dev.liamtolkkinen.sanctuary.companion;

import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class CompanionDebugCommand implements CommandExecutor {
    private CompanionDebugCommand() {
    }

    public static void register(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        var command = Objects.requireNonNull(
            plugin.getCommand("sanctuarydebugcompanions"),
            "sanctuarydebugcompanions command is missing from plugin.yml"
        );
        command.setExecutor(new CompanionDebugCommand());
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(
                    ChatColor.RED
                        + "Console must specify a target player: /sanctuarydebugcompanions <player>"
                );
                return true;
            }
            target = player;
        } else if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
        } else {
            sender.sendMessage(
                ChatColor.YELLOW + "Usage: /sanctuarydebugcompanions [player]"
            );
            return true;
        }

        int count = 0;
        for (CompanionDefinition definition : CompanionDefinition.ALL) {
            ItemStack egg = ExtendedItems.create(definition.itemId());
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(egg);
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            count++;
        }

        sender.sendMessage(
            ChatColor.GREEN
                + "Gave all "
                + count
                + " Sanctuary Companion Eggs to "
                + target.getName()
                + "."
        );
        if (!target.equals(sender)) {
            target.sendMessage(
                ChatColor.GOLD
                    + "You received all Sanctuary Companion Eggs for testing."
            );
        }
        return true;
    }
}

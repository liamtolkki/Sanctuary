package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.List;
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

public final class SanctuaryProgressionDebugCommand implements CommandExecutor {
    static final int TEST_STACK_AMOUNT = 64;

    private SanctuaryProgressionDebugCommand() {
    }

    public static void register(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        var command = Objects.requireNonNull(
            plugin.getCommand("sanctuarydebugshards"),
            "sanctuarydebugshards command is missing from plugin.yml"
        );
        command.setExecutor(new SanctuaryProgressionDebugCommand());
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
                        + "Console must specify a target player: /sanctuarydebugshards <player>"
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
                ChatColor.YELLOW + "Usage: /sanctuarydebugshards [player]"
            );
            return true;
        }

        for (ItemStack item : createTestStacks()) {
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        }

        sender.sendMessage(
            ChatColor.GREEN
                + "Gave 64 Consecrated Shard Fragments and 64 Consecrated Shards to "
                + target.getName()
                + "."
        );
        if (!target.equals(sender)) {
            target.sendMessage(
                ChatColor.GOLD
                    + "You received Sanctuary shard materials for testing."
            );
        }
        return true;
    }

    static List<ItemStack> createTestStacks() {
        ItemStack fragments = ExtendedItems.create(
            ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT
        );
        fragments.setAmount(TEST_STACK_AMOUNT);

        ItemStack shards = ExtendedItems.create(
            ExtendedItemIds.CONSECRATED_SHARD
        );
        shards.setAmount(TEST_STACK_AMOUNT);

        return List.of(fragments, shards);
    }
}

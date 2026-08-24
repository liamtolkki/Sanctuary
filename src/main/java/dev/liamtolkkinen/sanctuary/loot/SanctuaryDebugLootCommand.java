package dev.liamtolkkinen.sanctuary.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

/** Admin-only command that gives tagged structure-loot test chests. */
public final class SanctuaryDebugLootCommand implements CommandExecutor, TabCompleter {
    private final SanctuaryLootService lootService;

    public SanctuaryDebugLootCommand(SanctuaryLootService lootService) {
        this.lootService = Objects.requireNonNull(lootService, "lootService");
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length < 1 || args.length > 2) {
            sendUsage(sender, label);
            return true;
        }

        Player target;
        if (args.length == 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a target player.");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            for (ItemStack chest : lootService.createAllDebugChests()) {
                giveOrDrop(target, chest);
            }
            sender.sendMessage(
                ChatColor.GREEN + "Gave " + SanctuaryLootProfile.all().size()
                    + " Sanctuary debug loot chests to " + target.getName() + "."
            );
            return true;
        }

        SanctuaryLootProfile profile = SanctuaryLootProfile.parse(args[0]).orElse(null);
        if (profile == null) {
            sender.sendMessage(ChatColor.RED + "Unknown loot profile: " + args[0]);
            sendUsage(sender, label);
            return true;
        }

        giveOrDrop(target, lootService.createDebugChest(profile));
        sender.sendMessage(
            ChatColor.GREEN + "Gave " + profile.displayName()
                + " debug loot chest to " + target.getName() + "."
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
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> results = new ArrayList<>();
            if ("all".startsWith(prefix)) {
                results.add("all");
            }
            for (SanctuaryLootProfile profile : SanctuaryLootProfile.all()) {
                if (profile.id().startsWith(prefix)) {
                    results.add(profile.id());
                }
            }
            return results;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        return List.of();
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(
            ChatColor.YELLOW + "Usage: /" + label + " <profile|all> [player]"
        );
        sender.sendMessage(
            ChatColor.GRAY + "Profiles: "
                + String.join(
                    ", ",
                    SanctuaryLootProfile.all().stream()
                        .map(SanctuaryLootProfile::id)
                        .toList()
                )
        );
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}

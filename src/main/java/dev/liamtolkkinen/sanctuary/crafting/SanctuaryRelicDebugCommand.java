package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.altar.OfferingCatalog;
import java.util.ArrayList;
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

/** Admin-only test helper for the Divine Altar offering chain. */
public final class SanctuaryRelicDebugCommand implements CommandExecutor {
    private SanctuaryRelicDebugCommand() {
    }

    public static void register(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        var command = Objects.requireNonNull(
            plugin.getCommand("sanctuarydebugrelics"),
            "sanctuarydebugrelics command is missing from plugin.yml"
        );
        command.setExecutor(new SanctuaryRelicDebugCommand());
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
                        + "Console must specify a target player: /sanctuarydebugrelics <player>"
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
            sender.sendMessage(ChatColor.YELLOW + "Usage: /sanctuarydebugrelics [player]");
            return true;
        }

        for (ItemStack item : createTestItems()) {
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        }

        sender.sendMessage(
            ChatColor.GREEN + "Gave every Divine Altar offering item and the Divine Relic to "
                + target.getName() + "."
        );
        if (!target.equals(sender)) {
            target.sendMessage(
                ChatColor.GOLD + "You received all Sanctuary offering items for testing."
            );
        }
        return true;
    }

    static List<ItemStack> createTestItems() {
        List<ItemStack> items = new ArrayList<>();
        for (var offering : OfferingCatalog.all()) {
            items.add(createIngredientItem(offering.ingredient()));
        }
        items.add(ExtendedItems.create(ExtendedItemIds.DIVINE_RELIC));
        return List.copyOf(items);
    }

    private static ItemStack createIngredientItem(SanctuaryRecipeCatalog.Ingredient ingredient) {
        return ingredient.extendedItem() != null
            ? createExtendedItem(ingredient.extendedItem())
            : new ItemStack(ingredient.material());
    }

    private static ItemStack createExtendedItem(ExtendedItemId id) {
        return ExtendedItems.create(id);
    }
}

package dev.liamtolkkinen.sanctuary.altar;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Prevents the Divine Relic's Totem counterpart from acting as a vanilla Totem of Undying. */
public final class DivineRelicListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        ItemStack used = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();

        if (isDivineRelic(used)) {
            event.setCancelled(true);
        }
    }

    static boolean isDivineRelic(ItemStack item) {
        return item != null
            && !item.getType().isAir()
            && ExtendedItems.is(item, ExtendedItemIds.DIVINE_RELIC);
    }
}

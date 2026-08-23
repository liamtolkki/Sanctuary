package dev.liamtolkkinen.sanctuary.companion;

import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompanionEggState {
    private final NamespacedKey healthKey;

    public CompanionEggState(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.healthKey = new NamespacedKey(plugin, "companion_health");
    }

    public ItemStack createBaseEgg(CompanionDefinition definition) {
        return ExtendedItems.create(Objects.requireNonNull(definition, "definition").itemId());
    }

    public ItemStack createPickupEgg(Mob companion, CompanionDefinition definition) {
        Objects.requireNonNull(companion, "companion");
        ItemStack egg = createBaseEgg(definition);
        ItemMeta meta = egg.getItemMeta();
        meta.getPersistentDataContainer().set(
            healthKey,
            PersistentDataType.DOUBLE,
            companion.getHealth()
        );
        egg.setItemMeta(meta);
        return egg;
    }

    public void restoreHealth(ItemStack egg, Mob companion) {
        Objects.requireNonNull(companion, "companion");
        if (egg == null || !egg.hasItemMeta()) {
            return;
        }

        Double storedHealth = egg.getItemMeta()
            .getPersistentDataContainer()
            .get(healthKey, PersistentDataType.DOUBLE);
        if (storedHealth == null || !Double.isFinite(storedHealth) || storedHealth <= 0.0) {
            return;
        }

        AttributeInstance maxHealthAttribute = companion.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null
            ? companion.getHealth()
            : maxHealthAttribute.getValue();
        companion.setHealth(Math.min(storedHealth, maxHealth));
    }
}

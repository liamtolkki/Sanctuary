package dev.liamtolkkinen.sanctuary.companion;

import dev.liamtolkkinen.extendeditems.ExtendedItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
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
    static final int DISPLAY_MAX_DAMAGE = 1000;

    private final NamespacedKey healthKey;
    private final NamespacedKey maxHealthKey;

    public CompanionEggState(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.healthKey = new NamespacedKey(plugin, "companion_health");
        this.maxHealthKey = new NamespacedKey(plugin, "companion_max_health");
    }

    public ItemStack createBaseEgg(CompanionDefinition definition) {
        ItemStack egg = ExtendedItems.create(
            Objects.requireNonNull(definition, "definition").itemId()
        );
        applyHealthDisplay(egg, 1.0, 1.0);
        return egg;
    }

    public ItemStack createPickupEgg(Mob companion, CompanionDefinition definition) {
        Objects.requireNonNull(companion, "companion");
        ItemStack egg = createBaseEgg(definition);
        double currentHealth = companion.getHealth();
        double maxHealth = maxHealth(companion);

        ItemMeta meta = egg.getItemMeta();
        meta.getPersistentDataContainer().set(
            healthKey,
            PersistentDataType.DOUBLE,
            currentHealth
        );
        meta.getPersistentDataContainer().set(
            maxHealthKey,
            PersistentDataType.DOUBLE,
            maxHealth
        );
        egg.setItemMeta(meta);
        applyHealthDisplay(egg, currentHealth, maxHealth);
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

        companion.setHealth(Math.min(storedHealth, maxHealth(companion)));
    }

    static int displayDamage(double currentHealth, double maxHealth) {
        if (!Double.isFinite(currentHealth)
            || !Double.isFinite(maxHealth)
            || maxHealth <= 0.0) {
            return 0;
        }

        double clampedHealth = Math.max(0.0, Math.min(currentHealth, maxHealth));
        if (clampedHealth >= maxHealth) {
            return 0;
        }

        double healthLost = 1.0 - (clampedHealth / maxHealth);
        int damage = (int) Math.round(healthLost * DISPLAY_MAX_DAMAGE);
        return Math.max(1, Math.min(DISPLAY_MAX_DAMAGE - 1, damage));
    }

    private static void applyHealthDisplay(
        ItemStack egg,
        double currentHealth,
        double maxHealth
    ) {
        ItemMeta meta = egg.getItemMeta();
        meta.setMaxStackSize(1);
        meta.setUnbreakable(false);
        egg.setItemMeta(meta);

        egg.setData(DataComponentTypes.MAX_DAMAGE, DISPLAY_MAX_DAMAGE);
        egg.setData(DataComponentTypes.DAMAGE, displayDamage(currentHealth, maxHealth));
    }

    private static double maxHealth(Mob companion) {
        AttributeInstance maxHealthAttribute = companion.getAttribute(Attribute.MAX_HEALTH);
        return maxHealthAttribute == null
            ? companion.getHealth()
            : maxHealthAttribute.getValue();
    }
}

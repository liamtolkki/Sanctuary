package dev.liamtolkkinen.sanctuary.sentry;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryCraftingItemService {
    private final NamespacedKey trophyTypeKey;

    public SentryCraftingItemService(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.trophyTypeKey = new NamespacedKey(plugin, "sentry_trophy_type");
    }

    public ItemStack createSpecialIngredient(
        SentryRecipeCatalog.SpecialIngredient ingredient
    ) {
        Objects.requireNonNull(ingredient, "ingredient");

        return switch (ingredient) {
            case OMINOUS_BOTTLE_V -> createOminousBottleV();
            case CREEPER_TROPHY_HEAD ->
                createTrophyHead(Material.CREEPER_HEAD, "creeper", "Creeper Head");
            case ZOMBIE_TROPHY_HEAD ->
                createTrophyHead(Material.ZOMBIE_HEAD, "zombie", "Zombie Head");
            case PIGLIN_BRUTE_TROPHY_HEAD ->
                createTrophyHead(Material.PIGLIN_HEAD, "piglin_brute", "Piglin Brute Head");
        };
    }

    public boolean matchesSpecialIngredient(
        ItemStack item,
        SentryRecipeCatalog.SpecialIngredient ingredient
    ) {
        Objects.requireNonNull(ingredient, "ingredient");
        if (item == null || item.getType().isAir()) {
            return false;
        }

        return switch (ingredient) {
            case OMINOUS_BOTTLE_V -> isOminousBottleV(item);
            case CREEPER_TROPHY_HEAD,
                 ZOMBIE_TROPHY_HEAD,
                 PIGLIN_BRUTE_TROPHY_HEAD -> isTrophyHead(item, ingredient);
        };
    }

    public boolean isTrophyHead(
        ItemStack item,
        SentryRecipeCatalog.SpecialIngredient ingredient
    ) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        String expected = switch (ingredient) {
            case CREEPER_TROPHY_HEAD -> "creeper";
            case ZOMBIE_TROPHY_HEAD -> "zombie";
            case PIGLIN_BRUTE_TROPHY_HEAD -> "piglin_brute";
            case OMINOUS_BOTTLE_V -> null;
        };
        if (expected == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        String actual = meta.getPersistentDataContainer().get(
            trophyTypeKey,
            PersistentDataType.STRING
        );
        return expected.equals(actual);
    }

    private boolean isOminousBottleV(ItemStack item) {
        if (item.getType() != Material.OMINOUS_BOTTLE) {
            return false;
        }

        ItemMeta rawMeta = item.getItemMeta();
        return rawMeta instanceof OminousBottleMeta meta
            && meta.getAmplifier() == 4;
    }

    private ItemStack createOminousBottleV() {
        ItemStack item = new ItemStack(Material.OMINOUS_BOTTLE);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof OminousBottleMeta meta)) {
            throw new IllegalStateException("Ominous Bottle did not provide OminousBottleMeta");
        }

        meta.setAmplifier(4);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTrophyHead(
        Material material,
        String trophyType,
        String displayName
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException(material + " did not provide ItemMeta");
        }

        meta.displayName(Component.text(displayName));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(
            trophyTypeKey,
            PersistentDataType.STRING,
            trophyType
        );
        item.setItemMeta(meta);
        return item;
    }
}

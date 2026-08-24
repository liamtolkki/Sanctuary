package dev.liamtolkkinen.sanctuary.sentry;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

public final class SentryCraftingItemService {
    private static final List<Pattern> OMINOUS_BANNER_PATTERNS = List.of(
        new Pattern(DyeColor.CYAN, PatternType.RHOMBUS),
        new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_BOTTOM),
        new Pattern(DyeColor.GRAY, PatternType.STRIPE_CENTER),
        new Pattern(DyeColor.LIGHT_GRAY, PatternType.BORDER),
        new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE),
        new Pattern(DyeColor.LIGHT_GRAY, PatternType.HALF_HORIZONTAL),
        new Pattern(DyeColor.LIGHT_GRAY, PatternType.CIRCLE),
        new Pattern(DyeColor.BLACK, PatternType.BORDER)
    );

    public SentryCraftingItemService(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
    }

    public ItemStack createSpecialIngredient(
        SentryRecipeCatalog.SpecialIngredient ingredient
    ) {
        Objects.requireNonNull(ingredient, "ingredient");

        return switch (ingredient) {
            case OMINOUS_BOTTLE_V -> createOminousBottleV();
            case OMINOUS_BANNER -> createOminousBanner();
            case SPEED_II_POTION -> createSpeedIiPotion();
            case CREEPER_TROPHY_HEAD -> new ItemStack(Material.CREEPER_HEAD);
            case ZOMBIE_TROPHY_HEAD -> new ItemStack(Material.ZOMBIE_HEAD);
            case PIGLIN_BRUTE_TROPHY_HEAD -> new ItemStack(Material.PIGLIN_HEAD);
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
            case OMINOUS_BANNER -> isOminousBanner(item);
            case SPEED_II_POTION -> isSpeedIiPotion(item);
            case CREEPER_TROPHY_HEAD -> item.getType() == Material.CREEPER_HEAD;
            case ZOMBIE_TROPHY_HEAD -> item.getType() == Material.ZOMBIE_HEAD;
            case PIGLIN_BRUTE_TROPHY_HEAD -> item.getType() == Material.PIGLIN_HEAD;
        };
    }

    private boolean isOminousBottleV(ItemStack item) {
        if (item.getType() != Material.OMINOUS_BOTTLE) {
            return false;
        }

        ItemMeta rawMeta = item.getItemMeta();
        return rawMeta instanceof OminousBottleMeta meta
            && meta.getAmplifier() == 4;
    }

    private boolean isOminousBanner(ItemStack item) {
        if (item.getType() != Material.WHITE_BANNER) {
            return false;
        }
        ItemMeta rawMeta = item.getItemMeta();
        return rawMeta instanceof BannerMeta meta
            && meta.getPatterns().equals(OMINOUS_BANNER_PATTERNS);
    }

    private boolean isSpeedIiPotion(ItemStack item) {
        if (item.getType() != Material.POTION) {
            return false;
        }
        ItemMeta rawMeta = item.getItemMeta();
        return rawMeta instanceof PotionMeta meta
            && meta.getBasePotionType() == PotionType.STRONG_SWIFTNESS;
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

    private ItemStack createOminousBanner() {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof BannerMeta meta)) {
            throw new IllegalStateException("White Banner did not provide BannerMeta");
        }
        meta.setPatterns(OMINOUS_BANNER_PATTERNS);
        meta.displayName(Component.text("Ominous Banner"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSpeedIiPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof PotionMeta meta)) {
            throw new IllegalStateException("Potion did not provide PotionMeta");
        }
        meta.setBasePotionType(PotionType.STRONG_SWIFTNESS);
        item.setItemMeta(meta);
        return item;
    }
}

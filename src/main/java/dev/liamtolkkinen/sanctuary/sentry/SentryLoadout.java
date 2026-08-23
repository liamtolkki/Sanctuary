package dev.liamtolkkinen.sanctuary.sentry;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

final class SentryLoadout {
    private SentryLoadout() {
    }

    static void apply(Mob mob) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) return;

        if (mob instanceof Skeleton) {
            equipNetheriteArmor(equipment);
            equipment.setItemInMainHand(maxBow());
        } else if (mob instanceof Pillager) {
            equipNetheriteArmor(equipment);
            equipment.setItemInMainHand(maxCrossbow());
        } else if (mob instanceof Zombie || mob instanceof PiglinBrute || mob instanceof Drowned) {
            equipNetheriteArmor(equipment);
            equipment.setItemInMainHand(maxSword());
        } else if (mob.getType() == EntityType.ZOMBIFIED_PIGLIN || mob.getType() == EntityType.PIGLIN) {
            equipNetheriteArmor(equipment);
            equipment.setItemInMainHand(maxSword());
        }

        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private static void equipNetheriteArmor(EntityEquipment equipment) {
        equipment.setHelmet(armor(Material.NETHERITE_HELMET,
            Enchantment.PROTECTION, 4,
            Enchantment.UNBREAKING, 3,
            Enchantment.MENDING, 1,
            Enchantment.THORNS, 3,
            Enchantment.RESPIRATION, 3,
            Enchantment.AQUA_AFFINITY, 1));

        equipment.setChestplate(armor(Material.NETHERITE_CHESTPLATE,
            Enchantment.PROTECTION, 4,
            Enchantment.UNBREAKING, 3,
            Enchantment.MENDING, 1,
            Enchantment.THORNS, 3));

        equipment.setLeggings(armor(Material.NETHERITE_LEGGINGS,
            Enchantment.PROTECTION, 4,
            Enchantment.UNBREAKING, 3,
            Enchantment.MENDING, 1,
            Enchantment.THORNS, 3,
            Enchantment.SWIFT_SNEAK, 3));

        equipment.setBoots(armor(Material.NETHERITE_BOOTS,
            Enchantment.PROTECTION, 4,
            Enchantment.UNBREAKING, 3,
            Enchantment.MENDING, 1,
            Enchantment.THORNS, 3,
            Enchantment.FEATHER_FALLING, 4,
            Enchantment.DEPTH_STRIDER, 3,
            Enchantment.SOUL_SPEED, 3));
    }

    private static ItemStack maxSword() {
        return armor(Material.NETHERITE_SWORD,
            Enchantment.SHARPNESS, 5,
            Enchantment.UNBREAKING, 3,
            Enchantment.MENDING, 1,
            Enchantment.FIRE_ASPECT, 2,
            Enchantment.KNOCKBACK, 2);
    }

    private static ItemStack maxBow() {
        return armor(Material.BOW,
            Enchantment.POWER, 5,
            Enchantment.PUNCH, 2,
            Enchantment.FLAME, 1,
            Enchantment.INFINITY, 1,
            Enchantment.UNBREAKING, 3);
    }

    private static ItemStack maxCrossbow() {
        return armor(Material.CROSSBOW,
            Enchantment.QUICK_CHARGE, 3,
            Enchantment.MULTISHOT, 1,
            Enchantment.UNBREAKING, 3,
            Enchantment.MENDING, 1);
    }

    private static ItemStack armor(Material material, Object... enchantments) {
        ItemStack item = new ItemStack(material);
        for (int index = 0; index < enchantments.length; index += 2) {
            Enchantment enchantment = (Enchantment) enchantments[index];
            int level = (Integer) enchantments[index + 1];
            item.addUnsafeEnchantment(enchantment, level);
        }
        return item;
    }
}

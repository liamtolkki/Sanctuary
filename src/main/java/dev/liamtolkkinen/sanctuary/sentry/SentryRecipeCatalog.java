package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

public final class SentryRecipeCatalog {

    public enum SpecialIngredient {
        OMINOUS_BOTTLE_V,
        CREEPER_TROPHY_HEAD,
        ZOMBIE_TROPHY_HEAD,
        PIGLIN_BRUTE_TROPHY_HEAD
    }

    public record UnlockIngredient(
        Material material,
        SpecialIngredient special
    ) {
        public UnlockIngredient {
            if ((material == null) == (special == null)) {
                throw new IllegalArgumentException(
                    "Exactly one unlock material or special ingredient must be set"
                );
            }
        }

        public static UnlockIngredient material(Material material) {
            return new UnlockIngredient(
                Objects.requireNonNull(material, "material"),
                null
            );
        }

        public static UnlockIngredient special(SpecialIngredient special) {
            return new UnlockIngredient(
                null,
                Objects.requireNonNull(special, "special")
            );
        }
    }

    public record Ingredient(
        Material material,
        SpecialIngredient special,
        int count
    ) {
        public Ingredient {
            if ((material == null) == (special == null)) {
                throw new IllegalArgumentException(
                    "Exactly one of material or special must be set"
                );
            }
            if (count < 1) {
                throw new IllegalArgumentException("Ingredient count must be at least 1");
            }
        }

        public static Ingredient material(Material material, int count) {
            return new Ingredient(
                Objects.requireNonNull(material, "material"),
                null,
                count
            );
        }

        public static Ingredient special(SpecialIngredient special) {
            return new Ingredient(
                null,
                Objects.requireNonNull(special, "special"),
                1
            );
        }
    }

    public record CompanionRecipe(
        String key,
        ExtendedItemId result,
        UnlockIngredient unlockIngredient,
        List<Ingredient> ingredients
    ) {
        public CompanionRecipe {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Recipe key must not be blank");
            }
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(unlockIngredient, "unlockIngredient");
            ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));

            int slots = ingredients.stream().mapToInt(Ingredient::count).sum();
            if (slots < 1 || slots > 9) {
                throw new IllegalArgumentException(
                    "Companion recipe must use between 1 and 9 crafting slots"
                );
            }
        }

        public int slotCount() {
            return ingredients.stream().mapToInt(Ingredient::count).sum();
        }
    }

    public record SentryConversion(
        String key,
        ExtendedItemId companion,
        ExtendedItemId sentry,
        Material postMaterial
    ) {
        public SentryConversion {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Recipe key must not be blank");
            }
            Objects.requireNonNull(companion, "companion");
            Objects.requireNonNull(sentry, "sentry");
            Objects.requireNonNull(postMaterial, "postMaterial");
        }
    }

    private static final List<CompanionRecipe> COMPANION_RECIPES = List.of(
        companion(
            "companion_iron_golem",
            ExtendedItemIds.COMPANION_IRON_GOLEM,
            UnlockIngredient.material(Material.CARVED_PUMPKIN),
            Ingredient.material(Material.IRON_BLOCK, 7),
            Ingredient.material(Material.CARVED_PUMPKIN, 1)
        ),
        companion(
            "companion_pillager",
            ExtendedItemIds.COMPANION_PILLAGER,
            UnlockIngredient.special(SpecialIngredient.OMINOUS_BOTTLE_V),
            Ingredient.special(SpecialIngredient.OMINOUS_BOTTLE_V),
            Ingredient.material(Material.EMERALD_BLOCK, 4),
            Ingredient.material(Material.CROSSBOW, 4)
        ),
        companion(
            "companion_skeleton",
            ExtendedItemIds.COMPANION_SKELETON,
            UnlockIngredient.material(Material.SKELETON_SKULL),
            Ingredient.material(Material.SKELETON_SKULL, 3),
            Ingredient.material(Material.BONE_BLOCK, 3),
            Ingredient.material(Material.ARROW, 3)
        ),
        companion(
            "companion_piglin_brute",
            ExtendedItemIds.COMPANION_PIGLIN_BRUTE,
            UnlockIngredient.special(SpecialIngredient.PIGLIN_BRUTE_TROPHY_HEAD),
            Ingredient.special(SpecialIngredient.PIGLIN_BRUTE_TROPHY_HEAD),
            Ingredient.material(Material.GOLD_BLOCK, 4),
            Ingredient.material(Material.NETHERITE_SCRAP, 4)
        ),
        companion(
            "companion_evoker",
            ExtendedItemIds.COMPANION_EVOKER,
            UnlockIngredient.material(Material.TOTEM_OF_UNDYING),
            Ingredient.material(Material.TOTEM_OF_UNDYING, 8),
            Ingredient.special(SpecialIngredient.OMINOUS_BOTTLE_V)
        ),
        companion(
            "companion_baby_zombie",
            ExtendedItemIds.COMPANION_BABY_ZOMBIE,
            UnlockIngredient.special(SpecialIngredient.ZOMBIE_TROPHY_HEAD),
            Ingredient.special(SpecialIngredient.ZOMBIE_TROPHY_HEAD),
            Ingredient.material(Material.NETHERITE_HELMET, 1),
            Ingredient.material(Material.NETHERITE_CHESTPLATE, 1),
            Ingredient.material(Material.NETHERITE_LEGGINGS, 1),
            Ingredient.material(Material.NETHERITE_BOOTS, 1),
            Ingredient.material(Material.NETHERITE_SWORD, 1),
            Ingredient.material(Material.ROTTEN_FLESH, 3)
        ),
        companion(
            "companion_blaze",
            ExtendedItemIds.COMPANION_BLAZE,
            UnlockIngredient.material(Material.GHAST_TEAR),
            Ingredient.material(Material.BLAZE_ROD, 4),
            Ingredient.material(Material.MAGMA_CREAM, 4),
            Ingredient.material(Material.GHAST_TEAR, 1)
        ),
        companion(
            "companion_creeper",
            ExtendedItemIds.COMPANION_CREEPER,
            UnlockIngredient.special(SpecialIngredient.CREEPER_TROPHY_HEAD),
            Ingredient.special(SpecialIngredient.CREEPER_TROPHY_HEAD),
            Ingredient.material(Material.TNT, 4),
            Ingredient.material(Material.GUNPOWDER, 4)
        ),
        companion(
            "companion_wither",
            ExtendedItemIds.COMPANION_WITHER,
            UnlockIngredient.material(Material.NETHER_STAR),
            Ingredient.material(Material.WITHER_SKELETON_SKULL, 4),
            Ingredient.material(Material.WITHER_ROSE, 4),
            Ingredient.material(Material.NETHER_STAR, 1)
        ),
        companion(
            "companion_drowned",
            ExtendedItemIds.COMPANION_DROWNED,
            UnlockIngredient.material(Material.TRIDENT),
            Ingredient.material(Material.TRIDENT, 2),
            Ingredient.special(SpecialIngredient.ZOMBIE_TROPHY_HEAD),
            Ingredient.material(Material.ROTTEN_FLESH, 6)
        ),
        companion(
            "companion_guardian",
            ExtendedItemIds.COMPANION_GUARDIAN,
            UnlockIngredient.material(Material.HEART_OF_THE_SEA),
            Ingredient.material(Material.HEART_OF_THE_SEA, 1),
            Ingredient.material(Material.SPONGE, 8)
        ),
        companion(
            "companion_elder_guardian",
            ExtendedItemIds.COMPANION_ELDER_GUARDIAN,
            UnlockIngredient.material(Material.CONDUIT),
            Ingredient.material(Material.CONDUIT, 1),
            Ingredient.material(Material.SPONGE, 8)
        )
    );

    private static final List<SentryConversion> SENTRY_CONVERSIONS = List.of(
        conversion(
            "sentry_iron_golem",
            ExtendedItemIds.COMPANION_IRON_GOLEM,
            ExtendedItemIds.SENTRY_IRON_GOLEM,
            Material.SMOOTH_STONE_SLAB
        ),
        conversion(
            "sentry_pillager",
            ExtendedItemIds.COMPANION_PILLAGER,
            ExtendedItemIds.SENTRY_PILLAGER,
            Material.DARK_OAK_SLAB
        ),
        conversion(
            "sentry_skeleton",
            ExtendedItemIds.COMPANION_SKELETON,
            ExtendedItemIds.SENTRY_SKELETON,
            Material.QUARTZ_SLAB
        ),
        conversion(
            "sentry_piglin_brute",
            ExtendedItemIds.COMPANION_PIGLIN_BRUTE,
            ExtendedItemIds.SENTRY_PIGLIN_BRUTE,
            Material.BLACKSTONE_SLAB
        ),
        conversion(
            "sentry_enderman",
            ExtendedItemIds.COMPANION_ENDERMAN,
            ExtendedItemIds.SENTRY_ENDERMAN,
            Material.PURPUR_SLAB
        ),
        conversion(
            "sentry_evoker",
            ExtendedItemIds.COMPANION_EVOKER,
            ExtendedItemIds.SENTRY_EVOKER,
            Material.STONE_BRICK_SLAB
        ),
        conversion(
            "sentry_baby_zombie",
            ExtendedItemIds.COMPANION_BABY_ZOMBIE,
            ExtendedItemIds.SENTRY_BABY_ZOMBIE,
            Material.MOSSY_COBBLESTONE_SLAB
        ),
        conversion(
            "sentry_blaze",
            ExtendedItemIds.COMPANION_BLAZE,
            ExtendedItemIds.SENTRY_BLAZE,
            Material.NETHER_BRICK_SLAB
        ),
        conversion(
            "sentry_warden",
            ExtendedItemIds.COMPANION_WARDEN,
            ExtendedItemIds.SENTRY_WARDEN,
            Material.SCULK_SENSOR
        ),
        conversion(
            "sentry_creeper",
            ExtendedItemIds.COMPANION_CREEPER,
            ExtendedItemIds.SENTRY_CREEPER,
            Material.WAXED_WEATHERED_CUT_COPPER_SLAB
        ),
        conversion(
            "sentry_wither",
            ExtendedItemIds.COMPANION_WITHER,
            ExtendedItemIds.SENTRY_WITHER,
            Material.POLISHED_BLACKSTONE_SLAB
        ),
        conversion(
            "sentry_drowned",
            ExtendedItemIds.COMPANION_DROWNED,
            ExtendedItemIds.SENTRY_DROWNED,
            Material.PRISMARINE_SLAB
        ),
        conversion(
            "sentry_guardian",
            ExtendedItemIds.COMPANION_GUARDIAN,
            ExtendedItemIds.SENTRY_GUARDIAN,
            Material.DARK_PRISMARINE_SLAB
        ),
        conversion(
            "sentry_elder_guardian",
            ExtendedItemIds.COMPANION_ELDER_GUARDIAN,
            ExtendedItemIds.SENTRY_ELDER_GUARDIAN,
            Material.PRISMARINE_BRICK_SLAB
        )
    );

    private SentryRecipeCatalog() {
    }

    public static List<CompanionRecipe> companionRecipes() {
        return COMPANION_RECIPES;
    }

    public static List<SentryConversion> sentryConversions() {
        return SENTRY_CONVERSIONS;
    }

    private static CompanionRecipe companion(
        String key,
        ExtendedItemId result,
        UnlockIngredient unlockIngredient,
        Ingredient... ingredients
    ) {
        return new CompanionRecipe(
            key,
            result,
            unlockIngredient,
            List.of(ingredients)
        );
    }

    private static SentryConversion conversion(
        String key,
        ExtendedItemId companion,
        ExtendedItemId sentry,
        Material postMaterial
    ) {
        return new SentryConversion(key, companion, sentry, postMaterial);
    }
}

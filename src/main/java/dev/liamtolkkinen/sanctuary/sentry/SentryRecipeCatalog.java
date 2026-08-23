package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;

public final class SentryRecipeCatalog {

    public enum SpecialIngredient {
        OMINOUS_BOTTLE_V,
        OMINOUS_BANNER,
        SPEED_II_POTION,
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
        ExtendedItemId extendedItem,
        SpecialIngredient special
    ) {
        public Ingredient {
            int populated = (material == null ? 0 : 1)
                + (extendedItem == null ? 0 : 1)
                + (special == null ? 0 : 1);
            if (populated != 1) {
                throw new IllegalArgumentException(
                    "Exactly one ingredient type must be set"
                );
            }
        }

        public static Ingredient material(Material material) {
            return new Ingredient(
                Objects.requireNonNull(material, "material"),
                null,
                null
            );
        }

        public static Ingredient extended(ExtendedItemId extendedItem) {
            return new Ingredient(
                null,
                Objects.requireNonNull(extendedItem, "extendedItem"),
                null
            );
        }

        public static Ingredient special(SpecialIngredient special) {
            return new Ingredient(
                null,
                null,
                Objects.requireNonNull(special, "special")
            );
        }
    }

    public record CompanionRecipe(
        String key,
        ExtendedItemId result,
        UnlockIngredient unlockIngredient,
        List<String> shape,
        Map<Character, Ingredient> ingredients
    ) {
        public CompanionRecipe {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Recipe key must not be blank");
            }
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(unlockIngredient, "unlockIngredient");
            shape = List.copyOf(Objects.requireNonNull(shape, "shape"));
            ingredients = Map.copyOf(Objects.requireNonNull(ingredients, "ingredients"));

            if (shape.size() != 3) {
                throw new IllegalArgumentException(
                    "Companion recipe must have exactly three rows"
                );
            }
            for (String row : shape) {
                if (row == null || row.length() != 3) {
                    throw new IllegalArgumentException(
                        "Companion recipe rows must contain exactly three characters"
                    );
                }
                for (int column = 0; column < row.length(); column++) {
                    char symbol = row.charAt(column);
                    if (symbol != ' ' && !ingredients.containsKey(symbol)) {
                        throw new IllegalArgumentException(
                            "Recipe shape references unmapped ingredient symbol '"
                                + symbol + "'"
                        );
                    }
                }
            }

            for (Character symbol : ingredients.keySet()) {
                if (symbol == null || symbol == ' ') {
                    throw new IllegalArgumentException(
                        "Ingredient symbols must be non-space characters"
                    );
                }
                boolean used = shape.stream().anyMatch(row -> row.indexOf(symbol) >= 0);
                if (!used) {
                    throw new IllegalArgumentException(
                        "Ingredient symbol '" + symbol + "' is not used by the recipe shape"
                    );
                }
            }
        }

        public int slotCount() {
            return 9;
        }

        public Ingredient ingredientAt(int index) {
            if (index < 0 || index >= 9) {
                throw new IndexOutOfBoundsException("Companion recipe grid index: " + index);
            }
            char symbol = shape.get(index / 3).charAt(index % 3);
            return symbol == ' ' ? null : ingredients.get(symbol);
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

    private static final Ingredient S =
        Ingredient.extended(ExtendedItemIds.CONSECRATED_SHARD);

    private static final List<CompanionRecipe> COMPANION_RECIPES = List.of(
        companion(
            "companion_iron_golem",
            ExtendedItemIds.COMPANION_IRON_GOLEM,
            UnlockIngredient.material(Material.CARVED_PUMPKIN),
            "SPS",
            "III",
            "SIS",
            ingredients(
                'S', S,
                'P', Ingredient.material(Material.CARVED_PUMPKIN),
                'I', Ingredient.material(Material.IRON_BLOCK)
            )
        ),
        companion(
            "companion_pillager",
            ExtendedItemIds.COMPANION_PILLAGER,
            UnlockIngredient.special(SpecialIngredient.OMINOUS_BANNER),
            "SSS",
            "BCO",
            "SSS",
            ingredients(
                'S', S,
                'B', Ingredient.special(SpecialIngredient.OMINOUS_BANNER),
                'C', Ingredient.material(Material.CROSSBOW),
                'O', Ingredient.special(SpecialIngredient.OMINOUS_BOTTLE_V)
            )
        ),
        companion(
            "companion_skeleton",
            ExtendedItemIds.COMPANION_SKELETON,
            UnlockIngredient.material(Material.SKELETON_SKULL),
            "SSS",
            "BKA",
            "SSS",
            ingredients(
                'S', S,
                'B', Ingredient.material(Material.BOW),
                'K', Ingredient.material(Material.SKELETON_SKULL),
                'A', Ingredient.material(Material.ARROW)
            )
        ),
        companion(
            "companion_piglin_brute",
            ExtendedItemIds.COMPANION_PIGLIN_BRUTE,
            UnlockIngredient.special(SpecialIngredient.PIGLIN_BRUTE_TROPHY_HEAD),
            "SSS",
            "GHA",
            "NNN",
            ingredients(
                'S', S,
                'G', Ingredient.material(Material.GOLD_BLOCK),
                'H', Ingredient.special(SpecialIngredient.PIGLIN_BRUTE_TROPHY_HEAD),
                'A', Ingredient.material(Material.GOLDEN_AXE),
                'N', Ingredient.material(Material.NETHERITE_SCRAP)
            )
        ),
        companion(
            "companion_evoker",
            ExtendedItemIds.COMPANION_EVOKER,
            UnlockIngredient.special(SpecialIngredient.OMINOUS_BOTTLE_V),
            "STS",
            "TBT",
            "STS",
            ingredients(
                'S', S,
                'T', Ingredient.material(Material.TOTEM_OF_UNDYING),
                'B', Ingredient.special(SpecialIngredient.OMINOUS_BOTTLE_V)
            )
        ),
        companion(
            "companion_baby_zombie",
            ExtendedItemIds.COMPANION_BABY_ZOMBIE,
            UnlockIngredient.special(SpecialIngredient.ZOMBIE_TROPHY_HEAD),
            "SPS",
            "HZW",
            "SSS",
            ingredients(
                'S', S,
                'P', Ingredient.special(SpecialIngredient.SPEED_II_POTION),
                'H', Ingredient.material(Material.NETHERITE_HELMET),
                'Z', Ingredient.special(SpecialIngredient.ZOMBIE_TROPHY_HEAD),
                'W', Ingredient.material(Material.NETHERITE_SWORD)
            )
        ),
        companion(
            "companion_blaze",
            ExtendedItemIds.COMPANION_BLAZE,
            UnlockIngredient.material(Material.BLAZE_ROD),
            "RRR",
            "RSR",
            "RRR",
            ingredients(
                'R', Ingredient.material(Material.BLAZE_ROD),
                'S', S
            )
        ),
        companion(
            "companion_creeper",
            ExtendedItemIds.COMPANION_CREEPER,
            UnlockIngredient.special(SpecialIngredient.CREEPER_TROPHY_HEAD),
            "SSS",
            "THT",
            "SSS",
            ingredients(
                'S', S,
                'T', Ingredient.material(Material.TNT),
                'H', Ingredient.special(SpecialIngredient.CREEPER_TROPHY_HEAD)
            )
        ),
        companion(
            "companion_wither",
            ExtendedItemIds.COMPANION_WITHER,
            UnlockIngredient.material(Material.WITHER_SKELETON_SKULL),
            "KKK",
            "CDC",
            "SSS",
            ingredients(
                'K', Ingredient.material(Material.WITHER_SKELETON_SKULL),
                'C', Ingredient.extended(ExtendedItemIds.SANCTUARY_CORE),
                'D', Ingredient.extended(ExtendedItemIds.DIVINE_RELIC),
                'S', S
            )
        ),
        companion(
            "companion_drowned",
            ExtendedItemIds.COMPANION_DROWNED,
            UnlockIngredient.material(Material.TRIDENT),
            "SSS",
            "HZT",
            "SSS",
            ingredients(
                'S', S,
                'H', Ingredient.material(Material.NETHERITE_HELMET),
                'Z', Ingredient.special(SpecialIngredient.ZOMBIE_TROPHY_HEAD),
                'T', Ingredient.material(Material.TRIDENT)
            )
        ),
        companion(
            "companion_guardian",
            ExtendedItemIds.COMPANION_GUARDIAN,
            UnlockIngredient.material(Material.HEART_OF_THE_SEA),
            "SPS",
            "PHP",
            "SPS",
            ingredients(
                'S', S,
                'P', Ingredient.material(Material.SPONGE),
                'H', Ingredient.material(Material.HEART_OF_THE_SEA)
            )
        ),
        companion(
            "companion_elder_guardian",
            ExtendedItemIds.COMPANION_ELDER_GUARDIAN,
            UnlockIngredient.material(Material.CONDUIT),
            "SSS",
            "CHC",
            "SSS",
            ingredients(
                'S', S,
                'C', Ingredient.material(Material.CONDUIT),
                'H', Ingredient.material(Material.HEART_OF_THE_SEA)
            )
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
        String top,
        String middle,
        String bottom,
        Map<Character, Ingredient> ingredients
    ) {
        return new CompanionRecipe(
            key,
            result,
            unlockIngredient,
            List.of(top, middle, bottom),
            ingredients
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

    private static Map<Character, Ingredient> ingredients(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Ingredient map values must be symbol/value pairs"
            );
        }
        Map<Character, Ingredient> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            Character symbol = (Character) values[index];
            Ingredient ingredient = (Ingredient) values[index + 1];
            if (result.put(symbol, ingredient) != null) {
                throw new IllegalArgumentException(
                    "Duplicate ingredient symbol '" + symbol + "'"
                );
            }
        }
        return result;
    }
}

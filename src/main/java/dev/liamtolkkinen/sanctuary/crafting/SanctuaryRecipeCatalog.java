package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;

public final class SanctuaryRecipeCatalog {

    public sealed interface RecipeDefinition
        permits ShapedRecipeDefinition, ShapelessRecipeDefinition
    {
        String key();
        ExtendedItemId result();
    }

    public record Ingredient(Material material, ExtendedItemId extendedItem) {
        public Ingredient {
            if ((material == null) == (extendedItem == null)) {
                throw new IllegalArgumentException("Exactly one of material or extendedItem must be set");
            }
        }
        public static Ingredient material(Material material) {
            return new Ingredient(Objects.requireNonNull(material, "material"), null);
        }
        public static Ingredient extended(ExtendedItemId extendedItem) {
            return new Ingredient(null, Objects.requireNonNull(extendedItem, "extendedItem"));
        }
    }

    public record ShapedRecipeDefinition(String key, ExtendedItemId result, List<String> shape, Map<Character, Ingredient> ingredients) implements RecipeDefinition {
        public ShapedRecipeDefinition {
            validateKeyAndResult(key, result);
            shape = List.copyOf(Objects.requireNonNull(shape, "shape"));
            ingredients = Map.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
            if (shape.size() != 3) throw new IllegalArgumentException("Sanctuary shaped recipes must have exactly three rows");
            for (String row : shape) {
                if (row == null || row.length() != 3) throw new IllegalArgumentException("Sanctuary shaped recipe rows must contain exactly three characters");
                for (int index = 0; index < row.length(); index++) {
                    char symbol = row.charAt(index);
                    if (symbol != ' ' && !ingredients.containsKey(symbol)) throw new IllegalArgumentException("Recipe shape references unmapped ingredient symbol '" + symbol + "'");
                }
            }
            for (Character symbol : ingredients.keySet()) {
                if (symbol == null || symbol == ' ') throw new IllegalArgumentException("Ingredient symbols must be non-space characters");
                boolean used = shape.stream().anyMatch(row -> row.indexOf(symbol) >= 0);
                if (!used) throw new IllegalArgumentException("Ingredient symbol '" + symbol + "' is not used by the recipe shape");
            }
        }
    }

    public record ShapelessRecipeDefinition(String key, ExtendedItemId result, List<Ingredient> ingredients) implements RecipeDefinition {
        public ShapelessRecipeDefinition {
            validateKeyAndResult(key, result);
            ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
            if (ingredients.isEmpty() || ingredients.size() > 9) throw new IllegalArgumentException("Sanctuary shapeless recipes must use between one and nine ingredients");
        }
    }

    private static final Ingredient S = Ingredient.extended(ExtendedItemIds.CONSECRATED_SHARD);
    private static final Ingredient F = Ingredient.extended(ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT);
    private static final List<ShapelessRecipeDefinition> SHAPELESS_RECIPES = List.of();

    private static final List<ShapedRecipeDefinition> SHAPED_RECIPES = List.of(
        shaped("recipe_consecrated_shard", ExtendedItemIds.CONSECRATED_SHARD, "FF ", "FF ", "   ", ingredients('F', F)),
        shaped("recipe_sanctuary_beacon", ExtendedItemIds.SANCTUARY_BEACON, "SSS", "SBS", "QQQ", ingredients('S', S, 'B', Ingredient.material(Material.BEACON), 'Q', Ingredient.material(Material.QUARTZ_BLOCK))),
        shaped("recipe_sanctuary_core", ExtendedItemIds.SANCTUARY_CORE, "SSS", "SNS", "SSS", ingredients('S', S, 'N', Ingredient.material(Material.NETHER_STAR))),
        shaped("recipe_territory_keystone", ExtendedItemIds.TERRITORY_KEYSTONE, "SPS", "PLP", "SPS", ingredients('S', S, 'P', Ingredient.material(Material.ENDER_PEARL), 'L', Ingredient.material(Material.LODESTONE))),
        shaped("recipe_watchers_eye", ExtendedItemIds.WATCHERS_EYE, "SPS", "PEP", "SPS", ingredients('S', S, 'P', Ingredient.material(Material.ENDER_PEARL), 'E', Ingredient.material(Material.ENDER_EYE))),
        shaped("recipe_ward_stone", ExtendedItemIds.WARD_STONE, "SIS", "IOI", "SIS", ingredients('S', S, 'I', Ingredient.material(Material.IRON_BLOCK), 'O', Ingredient.material(Material.OBSIDIAN))),
        shaped("recipe_blast_ward", ExtendedItemIds.BLAST_WARD, "SGS", "GCG", "SGS", ingredients('S', S, 'G', Ingredient.material(Material.GUNPOWDER), 'C', Ingredient.material(Material.CRYING_OBSIDIAN))),
        shaped("recipe_purification_relic", ExtendedItemIds.PURIFICATION_RELIC, "SAS", "ATA", "SAS", ingredients('S', S, 'A', Ingredient.material(Material.GOLDEN_APPLE), 'T', Ingredient.material(Material.GHAST_TEAR))),
        shaped("recipe_seal_of_keeping", ExtendedItemIds.SEAL_OF_KEEPING, "SSS", "SHS", "SSS", ingredients('S', S, 'H', Ingredient.material(Material.SHULKER_SHELL))),
        shaped("recipe_guardian_token", ExtendedItemIds.GUARDIAN_TOKEN, "SNS", "NHN", "SNS", ingredients('S', S, 'N', Ingredient.material(Material.NAUTILUS_SHELL), 'H', Ingredient.material(Material.HEART_OF_THE_SEA))),
        shaped("recipe_sentinel_seal", ExtendedItemIds.SENTINEL_SEAL, "SCS", "CEC", "SCS", ingredients('S', S, 'C', Ingredient.material(Material.SCULK_CATALYST), 'E', Ingredient.material(Material.ECHO_SHARD))),
        shaped("recipe_consecrated_keystone", ExtendedItemIds.CONSECRATED_KEYSTONE, "SCS", "CKC", "SCS", ingredients('S', S, 'C', Ingredient.material(Material.CRYING_OBSIDIAN), 'K', Ingredient.extended(ExtendedItemIds.SANCTUARY_CORE))),
        shaped("recipe_sanctuary_conduit", ExtendedItemIds.SANCTUARY_CONDUIT, "SSS", "SCS", "SDS", ingredients('S', S, 'C', Ingredient.extended(ExtendedItemIds.SANCTUARY_CORE), 'D', Ingredient.material(Material.CONDUIT))),
        shaped("recipe_divine_altar", ExtendedItemIds.DIVINE_ALTAR, "S S", " L ", "S S", ingredients('S', S, 'L', Ingredient.material(Material.LECTERN)))
    );

    private static final List<RecipeDefinition> ALL_RECIPES;
    private static final Map<String, RecipeDefinition> BY_KEY;
    static {
        List<RecipeDefinition> all = new ArrayList<>(SHAPELESS_RECIPES.size() + SHAPED_RECIPES.size());
        all.addAll(SHAPELESS_RECIPES); all.addAll(SHAPED_RECIPES); ALL_RECIPES = List.copyOf(all);
        Map<String, RecipeDefinition> byKey = new LinkedHashMap<>();
        for (RecipeDefinition recipe : ALL_RECIPES) if (byKey.put(recipe.key(), recipe) != null) throw new IllegalStateException("Duplicate Sanctuary recipe key " + recipe.key());
        BY_KEY = Map.copyOf(byKey);
    }

    private SanctuaryRecipeCatalog() {}
    public static List<ShapelessRecipeDefinition> shapelessRecipes() { return SHAPELESS_RECIPES; }
    public static List<ShapedRecipeDefinition> shapedRecipes() { return SHAPED_RECIPES; }
    public static List<RecipeDefinition> allRecipes() { return ALL_RECIPES; }
    public static Optional<RecipeDefinition> findByKey(String key) { return key == null ? Optional.empty() : Optional.ofNullable(BY_KEY.get(key)); }
    public static Optional<RecipeDefinition> findByResult(ExtendedItemId result) { return result == null ? Optional.empty() : ALL_RECIPES.stream().filter(recipe -> recipe.result().equals(result)).findFirst(); }

    static List<String> compactShape(ShapedRecipeDefinition definition) {
        int minRow = 3;
        int maxRow = -1;
        int minColumn = 3;
        int maxColumn = -1;
        for (int row = 0; row < 3; row++) {
            String value = definition.shape().get(row);
            for (int column = 0; column < 3; column++) {
                if (value.charAt(column) == ' ') {
                    continue;
                }
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < 0) {
            throw new IllegalArgumentException("Sanctuary shaped recipe must contain at least one ingredient");
        }
        List<String> compact = new ArrayList<>(maxRow - minRow + 1);
        for (int row = minRow; row <= maxRow; row++) {
            compact.add(definition.shape().get(row).substring(minColumn, maxColumn + 1));
        }
        return List.copyOf(compact);
    }

    private static void validateKeyAndResult(String key, ExtendedItemId result) { if (key == null || key.isBlank()) throw new IllegalArgumentException("Recipe key must not be blank"); Objects.requireNonNull(result, "result"); }
    private static ShapedRecipeDefinition shaped(String key, ExtendedItemId result, String top, String middle, String bottom, Map<Character, Ingredient> ingredients) { return new ShapedRecipeDefinition(key, result, List.of(top, middle, bottom), ingredients); }
    private static ShapelessRecipeDefinition shapeless(String key, ExtendedItemId result, Ingredient... ingredients) { return new ShapelessRecipeDefinition(key, result, List.of(ingredients)); }
    private static Map<Character, Ingredient> ingredients(Object... values) {
        if (values.length % 2 != 0) throw new IllegalArgumentException("Ingredient map values must be symbol/value pairs");
        Map<Character, Ingredient> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) { Character symbol = (Character) values[index]; Ingredient ingredient = (Ingredient) values[index + 1]; if (result.put(symbol, ingredient) != null) throw new IllegalArgumentException("Duplicate ingredient symbol '" + symbol + "'"); }
        return result;
    }
}

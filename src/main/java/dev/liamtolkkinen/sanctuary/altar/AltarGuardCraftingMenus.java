package dev.liamtolkkinen.sanctuary.altar;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.StandardButtons;
import dev.liamtolkkinen.sanctuary.advancement.SanctuaryAdvancementService;
import dev.liamtolkkinen.sanctuary.sentry.SentryCraftingItemService;
import dev.liamtolkkinen.sanctuary.sentry.SentryRecipeCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Divine Altar menus and crafting behavior for companion eggs and sentry posts. */
final class AltarGuardCraftingMenus {
    private static final int[] RECIPE_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };
    private static final int[] CRAFTING_GRID_SLOTS = {
        11, 12, 13,
        20, 21, 22,
        29, 30, 31
    };
    private static final int SENTRY_SHARD_INDEX = 1;
    private static final int SENTRY_COMPANION_INDEX = 4;
    private static final int SENTRY_POST_INDEX = 7;

    private final SanctuaryAdvancementService advancementService;
    private final SentryCraftingItemService craftingItems;

    AltarGuardCraftingMenus(
        JavaPlugin plugin,
        SanctuaryAdvancementService advancementService
    ) {
        Objects.requireNonNull(plugin, "plugin");
        this.advancementService = Objects.requireNonNull(
            advancementService,
            "advancementService"
        );
        this.craftingItems = new SentryCraftingItemService(plugin);
    }

    ExtendedInventoryMenu companionsMenu() {
        return new CompanionRecipesMenu();
    }

    ExtendedInventoryMenu sentriesMenu() {
        return new SentryRecipesMenu();
    }

    static int[] craftingGridSlots() {
        return CRAFTING_GRID_SLOTS.clone();
    }

    static int[] sentryIngredientIndexes() {
        return new int[] {
            SENTRY_SHARD_INDEX,
            SENTRY_COMPANION_INDEX,
            SENTRY_POST_INDEX
        };
    }

    private final class CompanionRecipesMenu extends ExtendedInventoryMenu {
        private CompanionRecipesMenu() {
            super(5, "<aqua>Companions");
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            List<SentryRecipeCatalog.CompanionRecipe> recipes =
                SentryRecipeCatalog.companionRecipes();

            for (int index = 0; index < recipes.size() && index < RECIPE_SLOTS.length; index++) {
                GuardRecipe recipe = companionRecipe(recipes.get(index));
                menu.set(
                    RECIPE_SLOTS[index],
                    ExtendedButton.builder(() -> recipeResultIcon(recipe))
                        .onClick(click -> click.menu().open(new GuardRecipeDetailMenu(recipe)))
                        .build()
                );
            }

            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private final class SentryRecipesMenu extends ExtendedInventoryMenu {
        private SentryRecipesMenu() {
            super(5, "<gold>Sentries");
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            List<SentryRecipeCatalog.SentryConversion> recipes =
                SentryRecipeCatalog.sentryConversions();

            for (int index = 0; index < recipes.size() && index < RECIPE_SLOTS.length; index++) {
                GuardRecipe recipe = sentryRecipe(recipes.get(index));
                menu.set(
                    RECIPE_SLOTS[index],
                    ExtendedButton.builder(() -> recipeResultIcon(recipe))
                        .onClick(click -> click.menu().open(new GuardRecipeDetailMenu(recipe)))
                        .build()
                );
            }

            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private final class GuardRecipeDetailMenu extends ExtendedInventoryMenu {
        private final GuardRecipe recipe;

        private GuardRecipeDetailMenu(GuardRecipe recipe) {
            super(
                5,
                recipe.sentry()
                    ? "<gold>Sentry Recipe"
                    : "<aqua>Companion Recipe"
            );
            this.recipe = recipe;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Player player = context.player();

            GuardIngredient[] grid = recipe.grid();
            for (int index = 0; index < grid.length; index++) {
                GuardIngredient ingredient = grid[index];
                if (ingredient == null) {
                    continue;
                }
                menu.set(
                    CRAFTING_GRID_SLOTS[index],
                    ExtendedButton.builder(ingredientIcon(ingredient))
                        .enabled(false)
                        .build()
                );
            }

            boolean ready = hasAllIngredients(player, recipe);
            menu.set(
                24,
                ExtendedButton.builder(() -> recipeStatusIcon(player, recipe, ready))
                    .enabled(false)
                    .build()
            );

            ExtendedButton.Builder resultButton =
                ExtendedButton.builder(() -> craftResultIcon(recipe, ready));
            if (ready) {
                resultButton.onClick(
                    click -> craftAtAltar(click.player(), recipe, click.menu())
                );
            } else {
                resultButton.enabled(false);
            }
            menu.set(25, resultButton.build());

            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private GuardRecipe companionRecipe(SentryRecipeCatalog.CompanionRecipe definition) {
        GuardIngredient[] grid = new GuardIngredient[9];
        for (int index = 0; index < grid.length; index++) {
            SentryRecipeCatalog.Ingredient ingredient = definition.ingredientAt(index);
            if (ingredient != null) {
                grid[index] = guardIngredient(ingredient);
            }
        }
        return new GuardRecipe(definition.result(), grid, false);
    }

    private GuardRecipe sentryRecipe(SentryRecipeCatalog.SentryConversion definition) {
        GuardIngredient[] grid = new GuardIngredient[9];
        grid[SENTRY_SHARD_INDEX] = GuardIngredient.extended(
            ExtendedItemIds.CONSECRATED_SHARD
        );
        grid[SENTRY_COMPANION_INDEX] = GuardIngredient.extended(
            definition.companion()
        );
        grid[SENTRY_POST_INDEX] = GuardIngredient.material(
            definition.postMaterial()
        );
        return new GuardRecipe(definition.sentry(), grid, true);
    }

    private GuardIngredient guardIngredient(SentryRecipeCatalog.Ingredient ingredient) {
        if (ingredient.extendedItem() != null) {
            return GuardIngredient.extended(ingredient.extendedItem());
        }
        if (ingredient.special() != null) {
            return GuardIngredient.special(ingredient.special());
        }
        return GuardIngredient.material(ingredient.material());
    }

    private ItemStack recipeResultIcon(GuardRecipe recipe) {
        ItemStack item = ExtendedItems.create(recipe.result());
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.text("Click to view recipe", NamedTextColor.AQUA));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack craftResultIcon(GuardRecipe recipe, boolean ready) {
        ItemStack item = ExtendedItems.create(recipe.result());
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.empty());
            lore.add(Component.text(
                ready ? "Click to craft" : "Gather the missing ingredients to craft",
                ready ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack ingredientIcon(GuardIngredient ingredient) {
        if (ingredient.extendedItem() != null) {
            return ExtendedItems.create(ingredient.extendedItem());
        }
        if (ingredient.special() != null) {
            return craftingItems.createSpecialIngredient(ingredient.special());
        }
        return new ItemStack(ingredient.material());
    }

    private ItemStack recipeStatusIcon(
        Player player,
        GuardRecipe recipe,
        boolean ready
    ) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        Map<String, GuardIngredient> definitions = new LinkedHashMap<>();
        for (GuardIngredient ingredient : recipe.grid()) {
            if (ingredient == null) {
                continue;
            }
            String key = ingredientKey(ingredient);
            needed.merge(key, 1, Integer::sum);
            definitions.putIfAbsent(key, ingredient);
        }

        List<Component> lore = new ArrayList<>();
        for (var entry : needed.entrySet()) {
            GuardIngredient ingredient = definitions.get(entry.getKey());
            int have = countIngredient(player, ingredient);
            int need = entry.getValue();
            lore.add(Component.text(
                displayName(ingredient) + ": " + have + " / " + need,
                have >= need ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        lore.add(Component.empty());
        lore.add(Component.text(
            ready ? "You have every exact ingredient." : "Missing exact recipe ingredients.",
            ready ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));

        ItemStack status = new ItemStack(ready ? Material.LIME_DYE : Material.GRAY_DYE);
        status.editMeta(meta -> {
            meta.displayName(Component.text(
                ready ? "READY" : "NOT READY",
                ready ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
            meta.lore(lore);
        });
        return status;
    }

    private boolean hasAllIngredients(Player player, GuardRecipe recipe) {
        Map<String, Integer> required = new LinkedHashMap<>();
        Map<String, GuardIngredient> definitions = new LinkedHashMap<>();
        for (GuardIngredient ingredient : recipe.grid()) {
            if (ingredient == null) {
                continue;
            }
            String key = ingredientKey(ingredient);
            required.merge(key, 1, Integer::sum);
            definitions.putIfAbsent(key, ingredient);
        }

        for (var entry : required.entrySet()) {
            GuardIngredient ingredient = definitions.get(entry.getKey());
            if (countIngredient(player, ingredient) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private int countIngredient(Player player, GuardIngredient ingredient) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matchesIngredient(item, ingredient)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean matchesIngredient(ItemStack item, GuardIngredient ingredient) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (ingredient.extendedItem() != null) {
            return ExtendedItems.is(item, ingredient.extendedItem());
        }
        if (ingredient.special() != null) {
            return craftingItems.matchesSpecialIngredient(item, ingredient.special());
        }
        return item.getType() == ingredient.material()
            && ExtendedItems.getId(item).isEmpty();
    }

    private void craftAtAltar(
        Player player,
        GuardRecipe recipe,
        ExtendedMenuContext menu
    ) {
        if (!hasAllIngredients(player, recipe)) {
            player.sendMessage(Component.text(
                "You no longer have every exact ingredient for that recipe.",
                NamedTextColor.YELLOW
            ));
            menu.refresh();
            return;
        }

        List<ItemStack> consumed = new ArrayList<>();
        for (GuardIngredient ingredient : recipe.grid()) {
            if (ingredient == null) {
                continue;
            }
            ItemStack taken = takeIngredient(player.getInventory(), ingredient);
            if (taken == null) {
                rollbackIngredients(player, consumed);
                player.sendMessage(Component.text(
                    "The altar could not consume the exact recipe ingredients.",
                    NamedTextColor.YELLOW
                ));
                menu.refresh();
                return;
            }
            consumed.add(taken);
        }

        giveOrDrop(player, ExtendedItems.create(recipe.result()));
        if (recipe.sentry()) {
            advancementService.recordSentryCraft(player);
        }
        player.updateInventory();
        menu.refresh();
    }

    private ItemStack takeIngredient(
        PlayerInventory inventory,
        GuardIngredient ingredient
    ) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack candidate = storage[slot];
            if (!matchesIngredient(candidate, ingredient)) {
                continue;
            }

            ItemStack taken = candidate.clone();
            taken.setAmount(1);
            if (candidate.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                candidate.setAmount(candidate.getAmount() - 1);
                inventory.setItem(slot, candidate);
            }
            return taken;
        }
        return null;
    }

    private void rollbackIngredients(Player player, List<ItemStack> consumed) {
        for (ItemStack item : consumed) {
            giveOrDrop(player, item);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private String ingredientKey(GuardIngredient ingredient) {
        if (ingredient.extendedItem() != null) {
            return "extended:" + ingredient.extendedItem().persistentId();
        }
        if (ingredient.special() != null) {
            return "special:" + ingredient.special().name();
        }
        return "material:" + ingredient.material().getKey();
    }

    private String displayName(GuardIngredient ingredient) {
        if (ingredient.extendedItem() != null) {
            return pretty(ingredient.extendedItem().persistentId());
        }
        if (ingredient.special() != null) {
            return switch (ingredient.special()) {
                case OMINOUS_BOTTLE_V -> "Ominous Bottle V";
                case OMINOUS_BANNER -> "Ominous Banner";
                case SPEED_II_POTION -> "Potion of Swiftness II";
                case CREEPER_TROPHY_HEAD -> "Creeper Head";
                case ZOMBIE_TROPHY_HEAD -> "Zombie Head";
                case PIGLIN_BRUTE_TROPHY_HEAD -> "Piglin Brute Head";
            };
        }
        return pretty(ingredient.material().name());
    }

    private String pretty(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record GuardRecipe(
        ExtendedItemId result,
        GuardIngredient[] grid,
        boolean sentry
    ) {
        private GuardRecipe {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(grid, "grid");
            if (grid.length != 9) {
                throw new IllegalArgumentException(
                    "Guard altar recipe grid must contain exactly nine slots"
                );
            }
            grid = grid.clone();
        }

        @Override
        public GuardIngredient[] grid() {
            return grid.clone();
        }
    }

    private record GuardIngredient(
        Material material,
        ExtendedItemId extendedItem,
        SentryRecipeCatalog.SpecialIngredient special
    ) {
        private GuardIngredient {
            int populated = (material == null ? 0 : 1)
                + (extendedItem == null ? 0 : 1)
                + (special == null ? 0 : 1);
            if (populated != 1) {
                throw new IllegalArgumentException(
                    "Exactly one guard altar ingredient type must be set"
                );
            }
        }

        private static GuardIngredient material(Material material) {
            return new GuardIngredient(
                Objects.requireNonNull(material, "material"),
                null,
                null
            );
        }

        private static GuardIngredient extended(ExtendedItemId itemId) {
            return new GuardIngredient(
                null,
                Objects.requireNonNull(itemId, "itemId"),
                null
            );
        }

        private static GuardIngredient special(
            SentryRecipeCatalog.SpecialIngredient special
        ) {
            return new GuardIngredient(
                null,
                null,
                Objects.requireNonNull(special, "special")
            );
        }
    }
}

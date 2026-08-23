package dev.liamtolkkinen.sanctuary.crafting;

import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class SanctuaryRecipeValidator {
    public boolean matches(
        SanctuaryRecipeCatalog.RecipeDefinition definition,
        ItemStack[] matrix
    ) {
        if (definition == null || matrix == null) {
            return false;
        }

        if (definition instanceof SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped) {
            return matchesShaped(shaped, matrix);
        }
        if (definition instanceof SanctuaryRecipeCatalog.ShapelessRecipeDefinition shapeless) {
            return (matrix.length == 4 || matrix.length == 9)
                && matchesShapeless(shapeless, matrix);
        }
        return false;
    }

    private boolean matchesShaped(
        SanctuaryRecipeCatalog.ShapedRecipeDefinition definition,
        ItemStack[] matrix
    ) {
        int gridSize = matrix.length == 4 ? 2 : matrix.length == 9 ? 3 : -1;
        if (gridSize < 0) {
            return false;
        }

        List<String> shape = SanctuaryRecipeCatalog.compactShape(definition);
        int height = shape.size();
        int width = shape.getFirst().length();
        if (height > gridSize || width > gridSize) {
            return false;
        }

        for (int rowOffset = 0; rowOffset <= gridSize - height; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= gridSize - width; columnOffset++) {
                if (matchesAtOffset(definition, shape, matrix, gridSize, rowOffset, columnOffset)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAtOffset(
        SanctuaryRecipeCatalog.ShapedRecipeDefinition definition,
        List<String> shape,
        ItemStack[] matrix,
        int gridSize,
        int rowOffset,
        int columnOffset
    ) {
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                int shapeRow = row - rowOffset;
                int shapeColumn = column - columnOffset;
                char symbol = ' ';
                if (shapeRow >= 0 && shapeRow < shape.size()
                    && shapeColumn >= 0 && shapeColumn < shape.get(shapeRow).length()) {
                    symbol = shape.get(shapeRow).charAt(shapeColumn);
                }

                ItemStack item = matrix[row * gridSize + column];
                if (symbol == ' ') {
                    if (!isEmpty(item)) {
                        return false;
                    }
                    continue;
                }
                if (!matchesIngredient(item, definition.ingredients().get(symbol))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesShapeless(
        SanctuaryRecipeCatalog.ShapelessRecipeDefinition definition,
        ItemStack[] matrix
    ) {
        List<ItemStack> present = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (!isEmpty(item)) {
                present.add(item);
            }
        }

        if (present.size() != definition.ingredients().size()) {
            return false;
        }

        boolean[] used = new boolean[definition.ingredients().size()];
        for (ItemStack item : present) {
            boolean matched = false;
            for (int index = 0; index < definition.ingredients().size(); index++) {
                if (!used[index]
                    && matchesIngredient(item, definition.ingredients().get(index)))
                {
                    used[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesIngredient(
        ItemStack item,
        SanctuaryRecipeCatalog.Ingredient expected
    ) {
        if (isEmpty(item) || expected == null) {
            return false;
        }
        if (expected.extendedItem() != null) {
            return ExtendedItems.is(item, expected.extendedItem());
        }
        return item.getType() == expected.material()
            && ExtendedItems.getId(item).isEmpty();
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}

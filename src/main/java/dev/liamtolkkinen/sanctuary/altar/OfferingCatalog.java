package dev.liamtolkkinen.sanctuary.altar;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.sanctuary.crafting.SanctuaryRecipeCatalog;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

/** Ordered Divine Altar offering progression and XP rewards. */
public final class OfferingCatalog {
    public record Offering(
        int number,
        SanctuaryRecipeCatalog.Ingredient ingredient,
        int experiencePoints
    ) {
        public Offering {
            if (number < 1 || number > 12) {
                throw new IllegalArgumentException("Offering number must be between 1 and 12");
            }
            Objects.requireNonNull(ingredient, "ingredient");
            if (experiencePoints < 1) {
                throw new IllegalArgumentException("Offering experience must be positive");
            }
        }
    }

    private static final List<Offering> OFFERINGS = List.of(
        extended(1, ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT, 25),
        extended(2, ExtendedItemIds.CONSECRATED_SHARD, 50),
        material(3, Material.GOLDEN_APPLE, 75),
        material(4, Material.GHAST_TEAR, 100),
        extended(5, ExtendedItemIds.WATCHERS_EYE, 125),
        extended(6, ExtendedItemIds.ATTUNEMENT_RELIC, 150),
        material(7, Material.END_CRYSTAL, 175),
        extended(8, ExtendedItemIds.SANCTUARY_CORE, 200),
        extended(9, ExtendedItemIds.TERRITORY_KEYSTONE, 225),
        material(10, Material.TOTEM_OF_UNDYING, 250),
        material(11, Material.NETHER_STAR, 275),
        extended(12, ExtendedItemIds.CONSECRATED_KEYSTONE, 300)
    );

    private OfferingCatalog() {
    }

    public static List<Offering> all() {
        return OFFERINGS;
    }

    public static Offering byNumber(int number) {
        if (number < 1 || number > OFFERINGS.size()) {
            throw new IllegalArgumentException("Offering number must be between 1 and 12");
        }
        return OFFERINGS.get(number - 1);
    }

    private static Offering extended(
        int number,
        dev.liamtolkkinen.extendeditems.ExtendedItemId itemId,
        int experiencePoints
    ) {
        return new Offering(
            number,
            SanctuaryRecipeCatalog.Ingredient.extended(itemId),
            experiencePoints
        );
    }

    private static Offering material(int number, Material material, int experiencePoints) {
        return new Offering(
            number,
            SanctuaryRecipeCatalog.Ingredient.material(material),
            experiencePoints
        );
    }
}

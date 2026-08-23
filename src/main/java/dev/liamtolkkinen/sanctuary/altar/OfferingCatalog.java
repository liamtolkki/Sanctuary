package dev.liamtolkkinen.sanctuary.altar;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.List;
import java.util.Objects;

/** Ordered Divine Altar offering progression and XP rewards. */
public final class OfferingCatalog {
    public record Offering(int number, ExtendedItemId itemId, int experiencePoints) {
        public Offering {
            if (number < 1 || number > 12) {
                throw new IllegalArgumentException("Offering number must be between 1 and 12");
            }
            Objects.requireNonNull(itemId, "itemId");
            if (experiencePoints < 1) {
                throw new IllegalArgumentException("Offering experience must be positive");
            }
        }
    }

    private static final List<Offering> OFFERINGS = List.of(
        new Offering(1, ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT, 25),
        new Offering(2, ExtendedItemIds.CONSECRATED_SHARD, 50),
        new Offering(3, ExtendedItemIds.WATCHERS_EYE, 75),
        new Offering(4, ExtendedItemIds.WARD_STONE, 100),
        new Offering(5, ExtendedItemIds.BLAST_WARD, 125),
        new Offering(6, ExtendedItemIds.GUARDIAN_TOKEN, 150),
        new Offering(7, ExtendedItemIds.PURIFICATION_RELIC, 175),
        new Offering(8, ExtendedItemIds.TERRITORY_KEYSTONE, 200),
        new Offering(9, ExtendedItemIds.SEAL_OF_KEEPING, 225),
        new Offering(10, ExtendedItemIds.SENTINEL_SEAL, 250),
        new Offering(11, ExtendedItemIds.SANCTUARY_CORE, 275),
        new Offering(12, ExtendedItemIds.CONSECRATED_KEYSTONE, 300)
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
}

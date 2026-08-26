package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;

/** Defines the five-tier Sanctuary anchor progression and its required artifacts. */
public final class AnchorTierProgression {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;
    public static final double DEFAULT_STARTING_RADIUS = 20.0;

    private AnchorTierProgression() {
    }

    public static double radiusForTier(double maximumRadius, int tier) {
        if (!Double.isFinite(maximumRadius) || maximumRadius <= 0.0) {
            throw new IllegalArgumentException("maximumRadius must be finite and greater than zero");
        }
        if (maximumRadius < DEFAULT_STARTING_RADIUS) {
            throw new IllegalArgumentException(
                "maximumRadius must be at least the Tier I starting radius"
            );
        }
        validateTier(tier);

        double increment = (maximumRadius - DEFAULT_STARTING_RADIUS) / (MAX_TIER - MIN_TIER);
        return DEFAULT_STARTING_RADIUS + (increment * (tier - MIN_TIER));
    }

    public static ExtendedItemId requiredUpgradeItem(int currentTier) {
        validateTier(currentTier);
        if (currentTier >= MAX_TIER) {
            throw new IllegalStateException("Tier V anchors cannot be upgraded further");
        }
        return currentTier < 4
            ? ExtendedItemIds.SANCTUARY_CORE
            : ExtendedItemIds.CONSECRATED_KEYSTONE;
    }

    public static int nextTier(int currentTier) {
        validateTier(currentTier);
        if (currentTier >= MAX_TIER) {
            throw new IllegalStateException("Tier V anchors cannot be upgraded further");
        }
        return currentTier + 1;
    }

    public static void validateTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("tier must be between I and V");
        }
    }
}

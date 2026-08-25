package dev.liamtolkkinen.sanctuary.defense;

import org.bukkit.Location;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/** Shared target-validity rules for Sanctuary sentries and companions. */
public final class DefenseTargetingRules {
    public static final double MAX_VERTICAL_TARGET_DISTANCE = 8.0;

    private DefenseTargetingRules() {
    }

    public static boolean withinVerticalRange(Location center, LivingEntity target) {
        return center.getWorld() != null
            && target.getWorld() == center.getWorld()
            && Math.abs(target.getY() - center.getY()) <= MAX_VERTICAL_TARGET_DISTANCE;
    }

    public static boolean hasRequiredLineOfSight(Mob defender, LivingEntity target) {
        return !(defender instanceof Guardian guardian) || guardian.hasLineOfSight(target);
    }

    public static boolean isLocallyRelevant(Mob defender, Location center, LivingEntity target) {
        return withinVerticalRange(center, target)
            && hasRequiredLineOfSight(defender, target);
    }
}

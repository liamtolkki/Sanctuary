package dev.liamtolkkinen.sanctuary.companion;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

final class CompanionCombatMemory {
    static final Duration TARGET_TIMEOUT = Duration.ofSeconds(15);

    private record Target(UUID entityId, Instant expiresAt) {
    }

    private static final Map<UUID, Target> targets = new HashMap<>();

    private CompanionCombatMemory() {
    }

    static void remember(Player owner, LivingEntity target, Instant now) {
        if (owner == null || target == null || now == null
            || owner.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        targets.put(
            owner.getUniqueId(),
            new Target(target.getUniqueId(), now.plus(TARGET_TIMEOUT))
        );
    }

    static Optional<LivingEntity> target(Player owner, Instant now) {
        Target remembered = targets.get(owner.getUniqueId());
        if (remembered == null) {
            return Optional.empty();
        }
        if (!now.isBefore(remembered.expiresAt())) {
            targets.remove(owner.getUniqueId());
            return Optional.empty();
        }

        Entity entity = Bukkit.getEntity(remembered.entityId());
        if (!(entity instanceof LivingEntity living) || living.isDead()) {
            targets.remove(owner.getUniqueId());
            return Optional.empty();
        }
        return Optional.of(living);
    }
}

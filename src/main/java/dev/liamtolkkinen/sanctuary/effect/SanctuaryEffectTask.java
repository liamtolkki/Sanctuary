package dev.liamtolkkinen.sanctuary.effect;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.DoubleSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SanctuaryEffectTask implements Runnable {
    private static final int EFFECT_DURATION_TICKS = 60;

    private final SanctuaryRepository repository;
    private final TerritoryPresenceService presenceService;
    private final SanctuaryEffectService effectService;
    private final DoubleSupplier maximumRadiusSupplier;
    private final Logger logger;

    public SanctuaryEffectTask(
        SanctuaryRepository repository,
        TerritoryPresenceService presenceService,
        SanctuaryEffectService effectService,
        DoubleSupplier maximumRadiusSupplier,
        Logger logger
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.presenceService = Objects.requireNonNull(presenceService, "presenceService");
        this.effectService = Objects.requireNonNull(effectService, "effectService");
        this.maximumRadiusSupplier = Objects.requireNonNull(maximumRadiusSupplier, "maximumRadiusSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void start(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                applyForPlayer(player);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to evaluate Sanctuary Beacon effects for " + player.getName(), exception);
            }
        }
    }

    private void applyForPlayer(Player player) throws SQLException {
        List<Sanctuary> sanctuaries = repository.findActiveInWorld(player.getWorld().getName());
        Sanctuary sanctuary = presenceService.findCurrentSanctuary(
            sanctuaries,
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getZ()
        ).orElse(null);
        if (sanctuary == null || sanctuary.position().isEmpty()) {
            return;
        }

        SanctuaryPosition position = sanctuary.position().orElseThrow();
        double deltaX = player.getLocation().getX() - (position.x() + 0.5);
        double deltaZ = player.getLocation().getZ() - (position.z() + 0.5);
        double horizontalDistance = Math.hypot(deltaX, deltaZ);

        List<SanctuaryEffectService.ActiveSanctuaryEffect> activeEffects = effectService.activeEffects(
            sanctuary,
            player.getUniqueId(),
            horizontalDistance,
            maximumRadiusSupplier.getAsDouble()
        );

        boolean elytraSuppressed = false;
        for (SanctuaryEffectService.ActiveSanctuaryEffect active : activeEffects) {
            if (active.effect() == SanctuaryEffect.ELYTRA_DISABLED) {
                elytraSuppressed = true;
            }
            apply(player, active);
        }

        if (elytraSuppressed) {
            player.sendActionBar(
                Component.text("Sanctuary defenses active", NamedTextColor.RED)
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Elytra Disabled", NamedTextColor.GOLD))
            );
        }
    }

    private static void apply(Player player, SanctuaryEffectService.ActiveSanctuaryEffect active) {
        SanctuaryEffect effect = active.effect();
        if (effect == SanctuaryEffect.ELYTRA_DISABLED) {
            if (player.isGliding()) {
                player.setGliding(false);
            }
            return;
        }

        PotionEffectType type = switch (effect) {
            case REGENERATION -> PotionEffectType.REGENERATION;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case HASTE -> PotionEffectType.HASTE;
            case SPEED -> PotionEffectType.SPEED;
            case MINING_FATIGUE -> PotionEffectType.MINING_FATIGUE;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
            case BLINDNESS -> PotionEffectType.BLINDNESS;
            case WITHER -> PotionEffectType.WITHER;
            case ELYTRA_DISABLED -> throw new IllegalStateException("Elytra suppression is not a potion effect");
        };
        player.addPotionEffect(new PotionEffect(
            type,
            EFFECT_DURATION_TICKS,
            active.amplifier(),
            true,
            false,
            true
        ));
    }
}

package dev.liamtolkkinen.sanctuary.effect;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private final TerritoryPresenceService legacyPresenceService;
    private final AnchorTerritoryService anchorTerritoryService;
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
        this.legacyPresenceService = Objects.requireNonNull(presenceService, "presenceService");
        this.anchorTerritoryService = null;
        this.effectService = Objects.requireNonNull(effectService, "effectService");
        this.maximumRadiusSupplier = Objects.requireNonNull(maximumRadiusSupplier, "maximumRadiusSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public SanctuaryEffectTask(
        SanctuaryRepository repository,
        AnchorTerritoryService anchorTerritoryService,
        SanctuaryEffectService effectService,
        DoubleSupplier maximumRadiusSupplier,
        Logger logger
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.legacyPresenceService = null;
        this.anchorTerritoryService = Objects.requireNonNull(anchorTerritoryService, "anchorTerritoryService");
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
                if (anchorTerritoryService == null) {
                    applyLegacy(player);
                } else {
                    applyGraph(player);
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Failed to evaluate Sanctuary anchor effects for " + player.getName(), exception);
            }
        }
    }

    private void applyGraph(Player player) throws SQLException {
        List<SanctuaryAnchor> anchors = anchorTerritoryService.coveringAnchors(
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getZ()
        );
        if (anchors.isEmpty()) {
            return;
        }

        Map<AnchorEffect, SanctuaryEffectService.ActiveAnchorEffect> strongest =
            new EnumMap<>(AnchorEffect.class);
        for (SanctuaryAnchor anchor : anchors) {
            Sanctuary sanctuary = repository.findById(anchor.sanctuaryId()).orElse(null);
            if (sanctuary == null || anchor.position().isEmpty()) {
                continue;
            }
            SanctuaryPosition position = anchor.position().orElseThrow();
            double horizontalDistance = Math.hypot(
                player.getLocation().getX() - (position.x() + 0.5),
                player.getLocation().getZ() - (position.z() + 0.5)
            );
            for (SanctuaryEffectService.ActiveAnchorEffect active : effectService.activeAnchorEffects(
                sanctuary,
                anchor,
                player.getUniqueId(),
                horizontalDistance,
                maximumRadiusSupplier.getAsDouble()
            )) {
                strongest.merge(
                    active.effect(),
                    active,
                    (first, second) -> first.level() >= second.level() ? first : second
                );
            }
        }
        applyAnchorEffects(player, strongest.values());
    }

    private void applyLegacy(Player player) throws SQLException {
        List<Sanctuary> sanctuaries = repository.findActiveInWorld(player.getWorld().getName());
        Sanctuary sanctuary = legacyPresenceService.findCurrentSanctuary(
            sanctuaries,
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getZ()
        ).orElse(null);
        if (sanctuary == null || sanctuary.position().isEmpty()) {
            return;
        }

        SanctuaryPosition position = sanctuary.position().orElseThrow();
        double horizontalDistance = Math.hypot(
            player.getLocation().getX() - (position.x() + 0.5),
            player.getLocation().getZ() - (position.z() + 0.5)
        );
        applyLegacyEffects(player, effectService.activeEffects(
            sanctuary,
            player.getUniqueId(),
            horizontalDistance,
            maximumRadiusSupplier.getAsDouble()
        ));
    }

    private static void applyAnchorEffects(
        Player player,
        java.util.Collection<SanctuaryEffectService.ActiveAnchorEffect> effects
    ) {
        boolean elytraSuppressed = false;
        for (SanctuaryEffectService.ActiveAnchorEffect active : effects) {
            if (active.effect() == AnchorEffect.ELYTRA_DISABLED) {
                elytraSuppressed = true;
            }
            applyAnchor(player, active);
        }
        if (elytraSuppressed) {
            showElytraSuppressed(player);
        }
    }

    private static void applyLegacyEffects(
        Player player,
        java.util.Collection<SanctuaryEffectService.ActiveSanctuaryEffect> effects
    ) {
        boolean elytraSuppressed = false;
        for (SanctuaryEffectService.ActiveSanctuaryEffect active : effects) {
            if (active.effect() == SanctuaryEffect.ELYTRA_DISABLED) {
                elytraSuppressed = true;
            }
            applyLegacy(player, active);
        }
        if (elytraSuppressed) {
            showElytraSuppressed(player);
        }
    }

    private static void applyAnchor(Player player, SanctuaryEffectService.ActiveAnchorEffect active) {
        AnchorEffect effect = active.effect();
        if (effect == AnchorEffect.ELYTRA_DISABLED) {
            if (player.isGliding()) player.setGliding(false);
            return;
        }
        PotionEffectType type = switch (effect) {
            case REGENERATION -> PotionEffectType.REGENERATION;
            case RESISTANCE -> PotionEffectType.RESISTANCE;
            case STRENGTH -> PotionEffectType.STRENGTH;
            case HASTE -> PotionEffectType.HASTE;
            case SPEED -> PotionEffectType.SPEED;
            case NIGHT_VISION -> PotionEffectType.NIGHT_VISION;
            case DOLPHINS_GRACE -> PotionEffectType.DOLPHINS_GRACE;
            case MINING_FATIGUE -> PotionEffectType.MINING_FATIGUE;
            case WEAKNESS -> PotionEffectType.WEAKNESS;
            case BLINDNESS -> PotionEffectType.BLINDNESS;
            case WITHER -> PotionEffectType.WITHER;
            case SLOWNESS -> PotionEffectType.SLOWNESS;
            case ELYTRA_DISABLED -> throw new IllegalStateException("Elytra suppression is not a potion effect");
        };
        addPotion(player, type, active.amplifier());
    }

    private static void applyLegacy(Player player, SanctuaryEffectService.ActiveSanctuaryEffect active) {
        SanctuaryEffect effect = active.effect();
        if (effect == SanctuaryEffect.ELYTRA_DISABLED) {
            if (player.isGliding()) player.setGliding(false);
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
        addPotion(player, type, active.amplifier());
    }

    private static void addPotion(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(
            type,
            EFFECT_DURATION_TICKS,
            amplifier,
            true,
            false,
            true
        ));
    }

    private static void showElytraSuppressed(Player player) {
        player.sendActionBar(
            Component.text("Sanctuary defenses active", NamedTextColor.RED)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Elytra Disabled", NamedTextColor.GOLD))
        );
    }
}

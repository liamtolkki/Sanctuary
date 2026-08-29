package dev.liamtolkkinen.sanctuary.effect;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;

public final class ElytraSuppressionListener implements Listener {
    private final SanctuaryRepository repository;
    private final TerritoryPresenceService legacyPresenceService;
    private final AnchorTerritoryService anchorTerritoryService;
    private final SanctuaryEffectService effectService;
    private final DoubleSupplier maximumRadiusSupplier;
    private final Logger logger;

    public ElytraSuppressionListener(
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

    public ElytraSuppressionListener(
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        try {
            if (isElytraSuppressed(player)) {
                event.setCancelled(true);
                player.sendActionBar(
                    Component.text("Sanctuary defenses active", NamedTextColor.RED)
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Elytra Disabled", NamedTextColor.GOLD))
                );
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to evaluate Sanctuary Elytra suppression for " + player.getName(), exception);
        }
    }

    private boolean isElytraSuppressed(Player player) throws SQLException {
        return anchorTerritoryService != null ? isGraphElytraSuppressed(player) : isLegacyElytraSuppressed(player);
    }

    private boolean isGraphElytraSuppressed(Player player) throws SQLException {
        for (SanctuaryAnchor anchor : anchorTerritoryService.coveringAnchors(
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        )) {
            if (anchor.type() != dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType.BEACON
                || anchor.position().isEmpty()) {
                continue;
            }
            Sanctuary sanctuary = repository.findById(anchor.sanctuaryId()).orElse(null);
            if (sanctuary == null) {
                continue;
            }
            SanctuaryPosition position = anchor.position().orElseThrow();
            double territoryDistance = TerritoryCalculator.scaledDistance(
                position,
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()
            );
            if (effectService.activeAnchorEffects(
                sanctuary,
                anchor,
                player.getUniqueId(),
                territoryDistance,
                maximumRadiusSupplier.getAsDouble()
            ).stream().anyMatch(active -> active.effect() == AnchorEffect.ELYTRA_DISABLED)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLegacyElytraSuppressed(Player player) throws SQLException {
        List<Sanctuary> sanctuaries = repository.findActiveInWorld(player.getWorld().getName());
        Sanctuary sanctuary = legacyPresenceService.findCurrentSanctuary(
            sanctuaries,
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        ).orElse(null);
        if (sanctuary == null || sanctuary.position().isEmpty()) {
            return false;
        }
        SanctuaryPosition position = sanctuary.position().orElseThrow();
        double territoryDistance = TerritoryCalculator.scaledDistance(
            position,
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        );
        return effectService.activeEffects(
            sanctuary,
            player.getUniqueId(),
            territoryDistance,
            maximumRadiusSupplier.getAsDouble()
        ).stream().anyMatch(active -> active.effect() == SanctuaryEffect.ELYTRA_DISABLED);
    }
}

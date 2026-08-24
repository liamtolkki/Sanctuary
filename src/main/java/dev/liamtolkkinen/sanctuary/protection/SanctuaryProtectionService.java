package dev.liamtolkkinen.sanctuary.protection;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SanctuaryProtectionService {
    private final SanctuaryRepository sanctuaryRepository;
    private final TerritoryPresenceService legacyPresenceService;
    private final AnchorTerritoryService anchorTerritoryService;
    private final SanctuaryPermissionService permissionService;

    public SanctuaryProtectionService(
        SanctuaryRepository sanctuaryRepository,
        TerritoryPresenceService presenceService,
        SanctuaryPermissionService permissionService
    ) {
        this.sanctuaryRepository = Objects.requireNonNull(sanctuaryRepository, "sanctuaryRepository");
        this.legacyPresenceService = Objects.requireNonNull(presenceService, "presenceService");
        this.anchorTerritoryService = null;
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
    }

    public SanctuaryProtectionService(
        AnchorTerritoryService anchorTerritoryService,
        SanctuaryPermissionService permissionService
    ) {
        this.sanctuaryRepository = null;
        this.legacyPresenceService = null;
        this.anchorTerritoryService = Objects.requireNonNull(anchorTerritoryService, "anchorTerritoryService");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
    }

    public Optional<Sanctuary> findBlockingSanctuary(
        UUID playerId,
        SanctuaryCapability capability,
        String world,
        double x,
        double z
    ) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(world, "world");

        Optional<Sanctuary> sanctuary;
        if (anchorTerritoryService != null) {
            sanctuary = anchorTerritoryService.findCurrentSanctuary(world, x, z);
        } else {
            sanctuary = legacyPresenceService.findCurrentSanctuary(
                sanctuaryRepository.findActiveInWorld(world),
                world,
                x,
                z
            );
        }
        if (sanctuary.isEmpty()) {
            return Optional.empty();
        }

        Sanctuary current = sanctuary.orElseThrow();
        return permissionService.hasCapability(current, playerId, capability)
            ? Optional.empty()
            : Optional.of(current);
    }
}

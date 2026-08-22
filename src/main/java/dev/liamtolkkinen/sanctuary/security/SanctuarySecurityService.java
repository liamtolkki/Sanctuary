package dev.liamtolkkinen.sanctuary.security;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SanctuarySecurityService {
    private final SanctuarySecurityRepository repository;
    private final SanctuaryPermissionService permissionService;

    public SanctuarySecurityService(
        SanctuarySecurityRepository repository,
        SanctuaryPermissionService permissionService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
    }

    public SanctuarySecurityMode mode(Sanctuary sanctuary) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        return repository.getMode(sanctuary.id());
    }

    public void setMode(Sanctuary sanctuary, SanctuarySecurityMode mode) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        repository.setMode(sanctuary.id(), Objects.requireNonNull(mode, "mode"));
    }

    public SanctuaryRelationship relationship(Sanctuary sanctuary, UUID playerId) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        if (sanctuary.ownerId().equals(playerId)) {
            return SanctuaryRelationship.OWNER;
        }
        if (permissionService.isTrusted(sanctuary, playerId)) {
            return SanctuaryRelationship.TRUSTED;
        }
        if (repository.isBlacklisted(sanctuary.id(), playerId)) {
            return SanctuaryRelationship.BLACKLISTED;
        }
        return SanctuaryRelationship.NEUTRAL;
    }

    public SanctuaryThreat threat(Sanctuary sanctuary, UUID playerId) throws SQLException {
        SanctuaryRelationship relationship = relationship(sanctuary, playerId);
        return switch (relationship) {
            case OWNER, TRUSTED -> SanctuaryThreat.SAFE;
            case BLACKLISTED -> SanctuaryThreat.HOSTILE;
            case NEUTRAL -> mode(sanctuary) == SanctuarySecurityMode.LOCKDOWN
                ? SanctuaryThreat.HOSTILE
                : SanctuaryThreat.NEUTRAL;
        };
    }

    public List<SanctuaryBlacklistEntry> blacklistedPlayers(Sanctuary sanctuary) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        return repository.findBlacklistedPlayers(sanctuary.id());
    }

    public void blacklist(Sanctuary sanctuary, UUID playerId, Instant now) throws SQLException {
        requireNonOwner(sanctuary, playerId);
        Objects.requireNonNull(now, "now");
        if (permissionService.isTrusted(sanctuary, playerId)) {
            permissionService.untrust(sanctuary, playerId);
        }
        repository.addBlacklisted(sanctuary.id(), playerId, now);
    }

    public void unblacklist(Sanctuary sanctuary, UUID playerId) throws SQLException {
        requireNonOwner(sanctuary, playerId);
        repository.removeBlacklisted(sanctuary.id(), playerId);
    }

    public void prepareForTrust(Sanctuary sanctuary, UUID playerId) throws SQLException {
        requireNonOwner(sanctuary, playerId);
        repository.removeBlacklisted(sanctuary.id(), playerId);
    }

    public boolean isBlacklisted(Sanctuary sanctuary, UUID playerId) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        return repository.isBlacklisted(sanctuary.id(), playerId);
    }

    private static void requireNonOwner(Sanctuary sanctuary, UUID playerId) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        if (sanctuary.ownerId().equals(playerId)) {
            throw new IllegalArgumentException("The Sanctuary owner cannot be blacklisted.");
        }
    }
}

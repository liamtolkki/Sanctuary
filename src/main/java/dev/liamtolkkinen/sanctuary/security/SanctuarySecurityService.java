package dev.liamtolkkinen.sanctuary.security;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SanctuarySecurityService {
    public static final int LOCKDOWN_UNLOCK_TIER = 3;
    public static final Duration AGGRESSION_DURATION = Duration.ofMinutes(10);

    private final SanctuarySecurityRepository repository;
    private final SanctuaryPermissionService permissionService;

    public SanctuarySecurityService(
        SanctuarySecurityRepository repository,
        SanctuaryPermissionService permissionService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
    }

    public static boolean lockdownUnlocked(Sanctuary sanctuary) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        return sanctuary.tier() >= LOCKDOWN_UNLOCK_TIER;
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
        return threat(sanctuary, playerId, Instant.now());
    }

    public SanctuaryThreat threat(Sanctuary sanctuary, UUID playerId, Instant now) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");

        SanctuaryRelationship relationship = relationship(sanctuary, playerId);
        if (relationship == SanctuaryRelationship.OWNER) {
            return SanctuaryThreat.SAFE;
        }
        if (isAggressive(sanctuary, playerId, now)) {
            return SanctuaryThreat.HOSTILE;
        }
        return switch (relationship) {
            case OWNER -> SanctuaryThreat.SAFE;
            case TRUSTED -> SanctuaryThreat.SAFE;
            case BLACKLISTED -> SanctuaryThreat.HOSTILE;
            case NEUTRAL -> mode(sanctuary) == SanctuarySecurityMode.LOCKDOWN
                ? SanctuaryThreat.HOSTILE
                : SanctuaryThreat.NEUTRAL;
        };
    }

    public void markAggressive(Sanctuary sanctuary, UUID playerId, Instant now) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        if (sanctuary.ownerId().equals(playerId)) {
            return;
        }
        repository.setAggressionUntil(
            sanctuary.id(),
            playerId,
            now.plus(AGGRESSION_DURATION)
        );
    }

    public boolean isAggressive(Sanctuary sanctuary, UUID playerId) throws SQLException {
        return isAggressive(sanctuary, playerId, Instant.now());
    }

    public boolean isAggressive(Sanctuary sanctuary, UUID playerId, Instant now) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        if (sanctuary.ownerId().equals(playerId)) {
            return false;
        }

        var hostileUntil = repository.getAggressionUntil(sanctuary.id(), playerId);
        if (hostileUntil.isEmpty()) {
            return false;
        }
        if (!now.isBefore(hostileUntil.orElseThrow())) {
            repository.clearAggression(sanctuary.id(), playerId);
            return false;
        }
        return true;
    }

    public void forgiveTemporaryAggression(UUID playerId) throws SQLException {
        repository.clearAggressionForPlayer(Objects.requireNonNull(playerId, "playerId"));
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
        repository.clearAggression(sanctuary.id(), playerId);
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

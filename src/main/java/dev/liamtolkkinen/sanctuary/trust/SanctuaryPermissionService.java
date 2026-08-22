package dev.liamtolkkinen.sanctuary.trust;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SanctuaryPermissionService {
    private final SanctuaryTrustRepository repository;

    public SanctuaryPermissionService(SanctuaryTrustRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public boolean isTrusted(Sanctuary sanctuary, UUID playerId) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        return sanctuary.ownerId().equals(playerId)
            || repository.isTrusted(sanctuary.id(), playerId);
    }

    public boolean hasCapability(
        Sanctuary sanctuary,
        UUID playerId,
        SanctuaryCapability capability
    ) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(capability, "capability");

        if (sanctuary.ownerId().equals(playerId)) {
            return true;
        }
        if (!repository.isTrusted(sanctuary.id(), playerId)) {
            return false;
        }
        return repository.findCapabilities(sanctuary.id(), playerId).contains(capability);
    }

    public Set<SanctuaryCapability> effectiveCapabilities(
        Sanctuary sanctuary,
        UUID playerId
    ) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");

        if (sanctuary.ownerId().equals(playerId)) {
            return EnumSet.allOf(SanctuaryCapability.class);
        }
        if (!repository.isTrusted(sanctuary.id(), playerId)) {
            return Set.of();
        }
        return repository.findCapabilities(sanctuary.id(), playerId);
    }

    public void trust(Sanctuary sanctuary, UUID playerId, Instant now) throws SQLException {
        requireNonOwner(sanctuary, playerId);
        repository.addTrusted(sanctuary.id(), playerId, Objects.requireNonNull(now, "now"));
    }

    public void untrust(Sanctuary sanctuary, UUID playerId) throws SQLException {
        requireNonOwner(sanctuary, playerId);
        repository.removeTrusted(sanctuary.id(), playerId);
    }

    public void setCapability(
        Sanctuary sanctuary,
        UUID playerId,
        SanctuaryCapability capability,
        boolean allowed
    ) throws SQLException {
        requireNonOwner(sanctuary, playerId);
        if (!repository.isTrusted(sanctuary.id(), playerId)) {
            throw new IllegalStateException("That player is not trusted by this Sanctuary.");
        }
        repository.setCapability(sanctuary.id(), playerId, capability, allowed);
    }

    public List<SanctuaryTrustEntry> trustedPlayers(Sanctuary sanctuary) throws SQLException {
        return repository.findTrustedPlayers(sanctuary.id());
    }

    private static void requireNonOwner(Sanctuary sanctuary, UUID playerId) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(playerId, "playerId");
        if (sanctuary.ownerId().equals(playerId)) {
            throw new IllegalArgumentException("The Sanctuary owner already has every capability.");
        }
    }
}

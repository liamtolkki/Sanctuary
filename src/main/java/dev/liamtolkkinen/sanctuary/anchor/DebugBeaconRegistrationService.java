package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DebugBeaconRegistrationService {
    private final SanctuaryRepository repository;
    private final Clock clock;

    public DebugBeaconRegistrationService(SanctuaryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    DebugBeaconRegistrationService(SanctuaryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Sanctuary register(double territoryArea) throws SQLException {
        if (!Double.isFinite(territoryArea) || territoryArea <= 0.0) {
            throw new IllegalArgumentException(
                "territoryArea must be finite and greater than zero"
            );
        }

        UUID sanctuaryId = UUID.randomUUID();
        UUID syntheticOwnerId = syntheticOwnerId(sanctuaryId);
        Instant now = clock.instant();

        Sanctuary sanctuary = new Sanctuary(
            sanctuaryId,
            syntheticOwnerId,
            SanctuaryType.BEACON,
            "Debug Sanctuary " + sanctuaryId.toString().substring(0, 8),
            Optional.empty(),
            1,
            1,
            territoryArea,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            true,
            now,
            now
        );
        repository.save(sanctuary);
        return sanctuary;
    }

    public void remove(UUID sanctuaryId) throws SQLException {
        repository.delete(sanctuaryId);
    }

    static UUID syntheticOwnerId(UUID sanctuaryId) {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        long mostSignificantBits = sanctuaryId.getMostSignificantBits();
        long leastSignificantBits = sanctuaryId.getLeastSignificantBits();

        // UUID version 15 is reserved and is not used by standard Minecraft player UUIDs.
        mostSignificantBits = (mostSignificantBits & 0xffffffffffff0fffL)
            | 0x000000000000f000L;
        leastSignificantBits = (leastSignificantBits & 0x3fffffffffffffffL)
            | 0x8000000000000000L;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}

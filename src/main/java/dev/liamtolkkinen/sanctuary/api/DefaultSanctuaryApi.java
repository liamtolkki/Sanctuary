package dev.liamtolkkinen.sanctuary.api;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultSanctuaryApi implements SanctuaryApi {
    private final SanctuaryRepository repository;
    private final Logger logger;

    public DefaultSanctuaryApi(
        SanctuaryRepository repository,
        Logger logger
    ) {
        this.repository = repository;
        this.logger = logger;
    }

    @Override
    public Optional<SanctuaryView> getSanctuary(UUID sanctuaryId) {
        try {
            return repository.findById(sanctuaryId).map(DefaultSanctuaryApi::toView);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to query Sanctuary " + sanctuaryId, exception);
            return Optional.empty();
        }
    }

    @Override
    public List<SanctuaryView> getPlayerSanctuaries(UUID playerId) {
        try {
            return repository.findByOwner(playerId)
                .stream()
                .map(DefaultSanctuaryApi::toView)
                .toList();
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to query Sanctuaries for " + playerId, exception);
            return List.of();
        }
    }

    private static SanctuaryView toView(Sanctuary sanctuary) {
        Optional<SanctuaryPositionView> position = sanctuary.position()
            .map(DefaultSanctuaryApi::toView);

        return new SanctuaryView(
            sanctuary.id(),
            sanctuary.ownerId(),
            sanctuary.type(),
            sanctuary.name(),
            position,
            sanctuary.tier(),
            sanctuary.territoryArea(),
            sanctuary.state()
        );
    }

    private static SanctuaryPositionView toView(SanctuaryPosition position) {
        return new SanctuaryPositionView(
            position.world(),
            position.x(),
            position.y(),
            position.z()
        );
    }
}

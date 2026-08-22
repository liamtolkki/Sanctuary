package dev.liamtolkkinen.sanctuary.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SanctuaryApi {
    Optional<SanctuaryView> getSanctuary(UUID sanctuaryId);

    List<SanctuaryView> getPlayerSanctuaries(UUID playerId);
}

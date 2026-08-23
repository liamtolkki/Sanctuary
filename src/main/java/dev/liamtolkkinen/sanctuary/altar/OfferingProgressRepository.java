package dev.liamtolkkinen.sanctuary.altar;

import java.sql.SQLException;
import java.util.UUID;

public interface OfferingProgressRepository {
    int completedOfferings(UUID playerId) throws SQLException;

    boolean advance(UUID playerId, int expectedCompleted) throws SQLException;

    boolean divineRelicAwarded(UUID playerId) throws SQLException;

    void markDivineRelicAwarded(UUID playerId) throws SQLException;
}

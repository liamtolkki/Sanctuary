package dev.liamtolkkinen.sanctuary.sanctuary;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SanctuaryRepository {
    Optional<Sanctuary> findById(UUID id) throws SQLException;

    List<Sanctuary> findByOwner(UUID ownerId) throws SQLException;

    List<Sanctuary> findAll() throws SQLException;

    List<Sanctuary> findActiveInWorld(String world) throws SQLException;

    void delete(UUID id) throws SQLException;

    void save(Sanctuary sanctuary) throws SQLException;
}

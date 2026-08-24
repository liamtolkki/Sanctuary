package dev.liamtolkkinen.sanctuary.anchor;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SanctuaryAnchorRepository {
    Optional<SanctuaryAnchor> findById(UUID id) throws SQLException;

    List<SanctuaryAnchor> findBySanctuary(UUID sanctuaryId) throws SQLException;

    List<SanctuaryAnchor> findActiveInWorld(String world) throws SQLException;

    List<SanctuaryAnchor> findChildren(UUID anchorId) throws SQLException;

    void save(SanctuaryAnchor anchor) throws SQLException;

    void delete(UUID id) throws SQLException;
}

package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteSanctuaryAnchorRepository implements SanctuaryAnchorRepository {
    private final DatabaseManager databaseManager;

    public SqliteSanctuaryAnchorRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<SanctuaryAnchor> findById(UUID id) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("SELECT * FROM sanctuary_anchors WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    @Override
    public List<SanctuaryAnchor> findBySanctuary(UUID sanctuaryId) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                 SELECT * FROM sanctuary_anchors
                 WHERE sanctuary_id = ?
                 ORDER BY created_at ASC, id ASC
                 """)) {
            statement.setString(1, sanctuaryId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return readAll(result);
            }
        }
    }

    @Override
    public List<SanctuaryAnchor> findActiveInWorld(String world) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                 SELECT * FROM sanctuary_anchors
                 WHERE world = ? AND state = 'ACTIVE'
                 ORDER BY created_at ASC, id ASC
                 """)) {
            statement.setString(1, world);
            try (ResultSet result = statement.executeQuery()) {
                return readAll(result);
            }
        }
    }

    @Override
    public List<SanctuaryAnchor> findChildren(UUID anchorId) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                 SELECT * FROM sanctuary_anchors
                 WHERE parent_anchor_id = ? AND state != 'DESTROYED'
                 ORDER BY created_at ASC, id ASC
                 """)) {
            statement.setString(1, anchorId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return readAll(result);
            }
        }
    }

    @Override
    public void save(SanctuaryAnchor anchor) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO sanctuary_anchors (
                     id, sanctuary_id, parent_anchor_id, type,
                     world, x, y, z, tier, anchor_generation, territory_radius,
                     state, destroyed_at, destruction_reason, created_at, updated_at
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 ON CONFLICT(id) DO UPDATE SET
                     sanctuary_id = excluded.sanctuary_id,
                     parent_anchor_id = excluded.parent_anchor_id,
                     type = excluded.type,
                     world = excluded.world,
                     x = excluded.x,
                     y = excluded.y,
                     z = excluded.z,
                     tier = excluded.tier,
                     anchor_generation = excluded.anchor_generation,
                     territory_radius = excluded.territory_radius,
                     state = excluded.state,
                     destroyed_at = excluded.destroyed_at,
                     destruction_reason = excluded.destruction_reason,
                     updated_at = excluded.updated_at
                 """)) {
            statement.setString(1, anchor.id().toString());
            statement.setString(2, anchor.sanctuaryId().toString());
            if (anchor.parentAnchorId().isPresent()) {
                statement.setString(3, anchor.parentAnchorId().orElseThrow().toString());
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            statement.setString(4, anchor.type().name());
            if (anchor.position().isPresent()) {
                SanctuaryPosition position = anchor.position().orElseThrow();
                statement.setString(5, position.world());
                statement.setInt(6, position.x());
                statement.setInt(7, position.y());
                statement.setInt(8, position.z());
            } else {
                statement.setNull(5, Types.VARCHAR);
                statement.setNull(6, Types.INTEGER);
                statement.setNull(7, Types.INTEGER);
                statement.setNull(8, Types.INTEGER);
            }
            statement.setInt(9, anchor.tier());
            statement.setInt(10, anchor.generation());
            statement.setDouble(11, anchor.territoryRadius());
            statement.setString(12, anchor.state().name());
            if (anchor.destroyedAt().isPresent()) {
                statement.setString(13, anchor.destroyedAt().orElseThrow().toString());
            } else {
                statement.setNull(13, Types.VARCHAR);
            }
            if (anchor.destructionReason().isPresent()) {
                statement.setString(14, anchor.destructionReason().orElseThrow());
            } else {
                statement.setNull(14, Types.VARCHAR);
            }
            statement.setString(15, anchor.createdAt().toString());
            statement.setString(16, anchor.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(UUID id) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("DELETE FROM sanctuary_anchors WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    private static List<SanctuaryAnchor> readAll(ResultSet result) throws SQLException {
        List<SanctuaryAnchor> anchors = new ArrayList<>();
        while (result.next()) {
            anchors.add(read(result));
        }
        return List.copyOf(anchors);
    }

    private static SanctuaryAnchor read(ResultSet result) throws SQLException {
        String world = result.getString("world");
        Optional<SanctuaryPosition> position = world == null
            ? Optional.empty()
            : Optional.of(new SanctuaryPosition(
                world,
                result.getInt("x"),
                result.getInt("y"),
                result.getInt("z")
            ));
        String parent = result.getString("parent_anchor_id");
        String destroyedAt = result.getString("destroyed_at");
        return new SanctuaryAnchor(
            UUID.fromString(result.getString("id")),
            UUID.fromString(result.getString("sanctuary_id")),
            parent == null ? Optional.empty() : Optional.of(UUID.fromString(parent)),
            SanctuaryType.valueOf(result.getString("type")),
            position,
            result.getInt("tier"),
            result.getInt("anchor_generation"),
            result.getDouble("territory_radius"),
            SanctuaryState.valueOf(result.getString("state")),
            destroyedAt == null ? Optional.empty() : Optional.of(Instant.parse(destroyedAt)),
            Optional.ofNullable(result.getString("destruction_reason")),
            Instant.parse(result.getString("created_at")),
            Instant.parse(result.getString("updated_at"))
        );
    }
}

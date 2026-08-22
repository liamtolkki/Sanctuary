package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
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

public final class SqliteSanctuaryRepository implements SanctuaryRepository {
    private final DatabaseManager databaseManager;

    public SqliteSanctuaryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<Sanctuary> findById(UUID id) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT *
                FROM sanctuaries
                WHERE id = ?
                """)
        ) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                    ? Optional.of(readSanctuary(result))
                    : Optional.empty();
            }
        }
    }

    @Override
    public List<Sanctuary> findByOwner(UUID ownerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT *
                FROM sanctuaries
                WHERE owner_uuid = ?
                ORDER BY created_at ASC
                """)
        ) {
            statement.setString(1, ownerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<Sanctuary> sanctuaries = new ArrayList<>();
                while (result.next()) {
                    sanctuaries.add(readSanctuary(result));
                }
                return List.copyOf(sanctuaries);
            }
        }
    }

    @Override
    public void save(Sanctuary sanctuary) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                INSERT INTO sanctuaries (
                    id,
                    owner_uuid,
                    type,
                    name,
                    world,
                    x,
                    y,
                    z,
                    tier,
                    territory_area,
                    state,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    owner_uuid = excluded.owner_uuid,
                    type = excluded.type,
                    name = excluded.name,
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    tier = excluded.tier,
                    territory_area = excluded.territory_area,
                    state = excluded.state,
                    updated_at = excluded.updated_at
                """)
        ) {
            statement.setString(1, sanctuary.id().toString());
            statement.setString(2, sanctuary.ownerId().toString());
            statement.setString(3, sanctuary.type().name());
            statement.setString(4, sanctuary.name());

            if (sanctuary.position().isPresent()) {
                SanctuaryPosition position = sanctuary.position().orElseThrow();
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

            statement.setInt(9, sanctuary.tier());
            statement.setDouble(10, sanctuary.territoryArea());
            statement.setString(11, sanctuary.state().name());
            statement.setString(12, sanctuary.createdAt().toString());
            statement.setString(13, sanctuary.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private static Sanctuary readSanctuary(ResultSet result) throws SQLException {
        String world = result.getString("world");
        Optional<SanctuaryPosition> position;

        if (world == null) {
            position = Optional.empty();
        } else {
            position = Optional.of(new SanctuaryPosition(
                world,
                result.getInt("x"),
                result.getInt("y"),
                result.getInt("z")
            ));
        }

        return new Sanctuary(
            UUID.fromString(result.getString("id")),
            UUID.fromString(result.getString("owner_uuid")),
            SanctuaryType.valueOf(result.getString("type")),
            result.getString("name"),
            position,
            result.getInt("tier"),
            result.getDouble("territory_area"),
            SanctuaryState.valueOf(result.getString("state")),
            Instant.parse(result.getString("created_at")),
            Instant.parse(result.getString("updated_at"))
        );
    }
}

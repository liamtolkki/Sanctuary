package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.sentry.SentryOverride;
import dev.liamtolkkinen.sanctuary.sentry.SentryRecord;
import dev.liamtolkkinen.sanctuary.sentry.SentryRepository;
import dev.liamtolkkinen.sanctuary.sentry.SentryState;
import dev.liamtolkkinen.sanctuary.sentry.SentryTrigger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteSentryRepository implements SentryRepository {
    private final DatabaseManager databaseManager;

    public SqliteSentryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(SentryRecord sentry) throws SQLException {
        try (var c = databaseManager.openConnection(); var s = c.prepareStatement("""
            INSERT INTO sentries(id,sanctuary_id,item_id,world,x,y,z,entity_uuid,state,respawn_at,recall_deadline,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET sanctuary_id=excluded.sanctuary_id,item_id=excluded.item_id,world=excluded.world,
            x=excluded.x,y=excluded.y,z=excluded.z,entity_uuid=excluded.entity_uuid,state=excluded.state,
            respawn_at=excluded.respawn_at,recall_deadline=excluded.recall_deadline,updated_at=excluded.updated_at
            """)) {
            s.setString(1, sentry.id().toString()); s.setString(2, sentry.sanctuaryId().toString());
            s.setString(3, sentry.itemId()); s.setString(4, sentry.world()); s.setInt(5, sentry.x()); s.setInt(6, sentry.y()); s.setInt(7, sentry.z());
            s.setString(8, sentry.entityId().map(UUID::toString).orElse(null)); s.setString(9, sentry.state().name());
            s.setString(10, sentry.respawnAt().map(Instant::toString).orElse(null)); s.setString(11, sentry.recallDeadline().map(Instant::toString).orElse(null));
            s.setString(12, sentry.createdAt().toString()); s.setString(13, sentry.updatedAt().toString()); s.executeUpdate();
        }
    }

    @Override public void delete(UUID id) throws SQLException { execute("DELETE FROM sentries WHERE id=?", id.toString()); }
    @Override public Optional<SentryRecord> findById(UUID id) throws SQLException { return one("SELECT * FROM sentries WHERE id=?", id.toString()); }
    @Override public Optional<SentryRecord> findByEntity(UUID id) throws SQLException { return one("SELECT * FROM sentries WHERE entity_uuid=?", id.toString()); }
    @Override public Optional<SentryRecord> findByPost(String world, int x, int y, int z) throws SQLException {
        try (var c=databaseManager.openConnection(); var s=c.prepareStatement("SELECT * FROM sentries WHERE world=? AND x=? AND y=? AND z=?")) {
            s.setString(1,world);s.setInt(2,x);s.setInt(3,y);s.setInt(4,z);try(var r=s.executeQuery()){return r.next()?Optional.of(read(r)):Optional.empty();}
        }
    }
    @Override public List<SentryRecord> findBySanctuary(UUID id) throws SQLException { return many("SELECT * FROM sentries WHERE sanctuary_id=? ORDER BY created_at", id.toString()); }
    @Override public List<SentryRecord> findAll() throws SQLException { return many("SELECT * FROM sentries ORDER BY created_at", null); }

    @Override public boolean getDefault(UUID sanctuaryId, SentryTrigger trigger) throws SQLException {
        try (var c=databaseManager.openConnection();var s=c.prepareStatement("SELECT enabled FROM sanctuary_sentry_defaults WHERE sanctuary_id=? AND trigger_name=?")) {
            s.setString(1,sanctuaryId.toString());s.setString(2,trigger.name());try(var r=s.executeQuery()){return r.next()?r.getInt(1)!=0:trigger.defaultEnabled();}
        }
    }
    @Override public void setDefault(UUID sanctuaryId,SentryTrigger trigger,boolean enabled)throws SQLException{
        try(var c=databaseManager.openConnection();var s=c.prepareStatement("""
            INSERT INTO sanctuary_sentry_defaults(sanctuary_id,trigger_name,enabled) VALUES(?,?,?)
            ON CONFLICT(sanctuary_id,trigger_name) DO UPDATE SET enabled=excluded.enabled
            """)){s.setString(1,sanctuaryId.toString());s.setString(2,trigger.name());s.setInt(3,enabled?1:0);s.executeUpdate();}
    }
    @Override public SentryOverride getOverride(UUID sentryId,SentryTrigger trigger)throws SQLException{
        try(var c=databaseManager.openConnection();var s=c.prepareStatement("SELECT override_value FROM sentry_overrides WHERE sentry_id=? AND trigger_name=?")){
            s.setString(1,sentryId.toString());s.setString(2,trigger.name());try(var r=s.executeQuery()){return r.next()?SentryOverride.valueOf(r.getString(1)):SentryOverride.INHERIT;}
        }
    }
    @Override public void setOverride(UUID sentryId,SentryTrigger trigger,SentryOverride value)throws SQLException{
        if(value==SentryOverride.INHERIT){try(var c=databaseManager.openConnection();var s=c.prepareStatement("DELETE FROM sentry_overrides WHERE sentry_id=? AND trigger_name=?")){s.setString(1,sentryId.toString());s.setString(2,trigger.name());s.executeUpdate();}return;}
        try(var c=databaseManager.openConnection();var s=c.prepareStatement("""
            INSERT INTO sentry_overrides(sentry_id,trigger_name,override_value) VALUES(?,?,?)
            ON CONFLICT(sentry_id,trigger_name) DO UPDATE SET override_value=excluded.override_value
            """)){s.setString(1,sentryId.toString());s.setString(2,trigger.name());s.setString(3,value.name());s.executeUpdate();}
    }
    @Override public void clearOverrides(UUID sentryId)throws SQLException{execute("DELETE FROM sentry_overrides WHERE sentry_id=?",sentryId.toString());}

    private Optional<SentryRecord> one(String sql,String value)throws SQLException{List<SentryRecord> rows=many(sql,value);return rows.stream().findFirst();}
    private List<SentryRecord> many(String sql,String value)throws SQLException{List<SentryRecord> rows=new ArrayList<>();try(var c=databaseManager.openConnection();var s=c.prepareStatement(sql)){if(value!=null)s.setString(1,value);try(var r=s.executeQuery()){while(r.next())rows.add(read(r));}}return rows;}
    private void execute(String sql,String value)throws SQLException{try(var c=databaseManager.openConnection();var s=c.prepareStatement(sql)){s.setString(1,value);s.executeUpdate();}}
    private static SentryRecord read(ResultSet r)throws SQLException{
        return new SentryRecord(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("sanctuary_id")),r.getString("item_id"),r.getString("world"),r.getInt("x"),r.getInt("y"),r.getInt("z"),
            Optional.ofNullable(r.getString("entity_uuid")).map(UUID::fromString),SentryState.valueOf(r.getString("state")),Optional.ofNullable(r.getString("respawn_at")).map(Instant::parse),Optional.ofNullable(r.getString("recall_deadline")).map(Instant::parse),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")));
    }
}

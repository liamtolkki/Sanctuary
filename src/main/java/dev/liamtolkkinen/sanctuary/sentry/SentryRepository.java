package dev.liamtolkkinen.sanctuary.sentry;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SentryRepository {
    void save(SentryRecord sentry) throws SQLException;
    void delete(UUID sentryId) throws SQLException;
    Optional<SentryRecord> findById(UUID sentryId) throws SQLException;
    Optional<SentryRecord> findByEntity(UUID entityId) throws SQLException;
    Optional<SentryRecord> findByPost(String world, int x, int y, int z) throws SQLException;
    List<SentryRecord> findBySanctuary(UUID sanctuaryId) throws SQLException;
    List<SentryRecord> findAll() throws SQLException;
    boolean getDefault(UUID sanctuaryId, SentryTrigger trigger) throws SQLException;
    void setDefault(UUID sanctuaryId, SentryTrigger trigger, boolean enabled) throws SQLException;
    SentryOverride getOverride(UUID sentryId, SentryTrigger trigger) throws SQLException;
    void setOverride(UUID sentryId, SentryTrigger trigger, SentryOverride value) throws SQLException;
    void clearOverrides(UUID sentryId) throws SQLException;
}

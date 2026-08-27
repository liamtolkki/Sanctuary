package dev.liamtolkkinen.sanctuary.upgrade;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public interface UpgradeRepository {
    boolean hasAnchorUpgrade(UUID anchorId, AnchorUpgradeType upgrade) throws SQLException;
    void installAnchorUpgrade(UUID anchorId, AnchorUpgradeType upgrade, Instant installedAt) throws SQLException;
    boolean hasSanctuaryUpgrade(UUID sanctuaryId, SanctuaryUpgradeType upgrade) throws SQLException;
    void installSanctuaryUpgrade(UUID sanctuaryId, SanctuaryUpgradeType upgrade, Instant installedAt) throws SQLException;
}

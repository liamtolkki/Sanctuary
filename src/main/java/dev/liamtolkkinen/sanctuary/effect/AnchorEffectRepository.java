package dev.liamtolkkinen.sanctuary.effect;

import java.sql.SQLException;
import java.util.UUID;

public interface AnchorEffectRepository {
    int getLevel(UUID anchorId, SanctuaryEffect effect) throws SQLException;

    void setLevel(UUID anchorId, SanctuaryEffect effect, int level) throws SQLException;
}

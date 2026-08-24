package dev.liamtolkkinen.sanctuary.effect;

import java.sql.SQLException;
import java.util.UUID;

public interface AnchorEffectRepository {
    int getLevel(UUID anchorId, AnchorEffect effect) throws SQLException;

    void setLevel(UUID anchorId, AnchorEffect effect, int level) throws SQLException;
}

package dev.liamtolkkinen.sanctuary.effect;

import java.sql.SQLException;
import java.util.UUID;

public interface SanctuaryEffectRepository {
    int getLevel(UUID sanctuaryId, SanctuaryEffect effect) throws SQLException;

    void setLevel(UUID sanctuaryId, SanctuaryEffect effect, int level) throws SQLException;
}

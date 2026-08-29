package dev.liamtolkkinen.sanctuary.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSanctuarySecurityRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void modeBlacklistAndAggressionPersistAndCascade() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("sanctuary.db"));
        new MigrationRunner(databaseManager).migrate();
        SqliteSanctuaryRepository sanctuaryRepository = new SqliteSanctuaryRepository(databaseManager);
        SqliteSanctuarySecurityRepository securityRepository =
            new SqliteSanctuarySecurityRepository(databaseManager);

        UUID sanctuaryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        sanctuaryRepository.save(new Sanctuary(
            sanctuaryId,
            ownerId,
            SanctuaryType.BEACON,
            "Security Test",
            Optional.empty(),
            1,
            1,
            18.0,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        ));

        assertEquals(SanctuarySecurityMode.NORMAL, securityRepository.getMode(sanctuaryId));
        securityRepository.setMode(sanctuaryId, SanctuarySecurityMode.LOCKDOWN);
        assertEquals(SanctuarySecurityMode.LOCKDOWN, securityRepository.getMode(sanctuaryId));

        securityRepository.addBlacklisted(sanctuaryId, playerId, now);
        assertTrue(securityRepository.isBlacklisted(sanctuaryId, playerId));
        assertEquals(1, securityRepository.findBlacklistedPlayers(sanctuaryId).size());

        securityRepository.removeBlacklisted(sanctuaryId, playerId);
        assertFalse(securityRepository.isBlacklisted(sanctuaryId, playerId));

        Instant hostileUntil = now.plusSeconds(600);
        securityRepository.setAggressionUntil(sanctuaryId, playerId, hostileUntil);
        assertEquals(
            Optional.of(hostileUntil),
            securityRepository.getAggressionUntil(sanctuaryId, playerId)
        );
        securityRepository.clearAggression(sanctuaryId, playerId);
        assertEquals(Optional.empty(), securityRepository.getAggressionUntil(sanctuaryId, playerId));

        securityRepository.addBlacklisted(sanctuaryId, playerId, now);
        securityRepository.setAggressionUntil(sanctuaryId, playerId, hostileUntil);
        sanctuaryRepository.delete(sanctuaryId);
        assertFalse(securityRepository.isBlacklisted(sanctuaryId, playerId));
        assertEquals(Optional.empty(), securityRepository.getAggressionUntil(sanctuaryId, playerId));
        assertEquals(SanctuarySecurityMode.NORMAL, securityRepository.getMode(sanctuaryId));
    }
}

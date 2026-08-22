package dev.liamtolkkinen.sanctuary.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSanctuaryTrustRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void trustAndCapabilitiesPersistAndCascade() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("sanctuary.db"));
        new MigrationRunner(databaseManager).migrate();
        SqliteSanctuaryRepository sanctuaryRepository = new SqliteSanctuaryRepository(databaseManager);
        SqliteSanctuaryTrustRepository trustRepository = new SqliteSanctuaryTrustRepository(databaseManager);
        Sanctuary sanctuary = sanctuary();
        UUID playerId = UUID.randomUUID();

        sanctuaryRepository.save(sanctuary);
        trustRepository.addTrusted(sanctuary.id(), playerId, Instant.parse("2026-08-22T12:01:00Z"));
        trustRepository.setCapability(sanctuary.id(), playerId, SanctuaryCapability.CONTAINER, true);
        trustRepository.setCapability(sanctuary.id(), playerId, SanctuaryCapability.INTERACT, true);

        assertTrue(trustRepository.isTrusted(sanctuary.id(), playerId));
        assertEquals(
            java.util.Set.of(SanctuaryCapability.CONTAINER, SanctuaryCapability.INTERACT),
            trustRepository.findCapabilities(sanctuary.id(), playerId)
        );
        assertEquals(1, trustRepository.findTrustedPlayers(sanctuary.id()).size());

        trustRepository.removeTrusted(sanctuary.id(), playerId);
        assertFalse(trustRepository.isTrusted(sanctuary.id(), playerId));
        assertTrue(trustRepository.findCapabilities(sanctuary.id(), playerId).isEmpty());
    }

    @Test
    void deletingSanctuaryCascadesTrustRows() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("cascade.db"));
        new MigrationRunner(databaseManager).migrate();
        SqliteSanctuaryRepository sanctuaryRepository = new SqliteSanctuaryRepository(databaseManager);
        SqliteSanctuaryTrustRepository trustRepository = new SqliteSanctuaryTrustRepository(databaseManager);
        Sanctuary sanctuary = sanctuary();
        UUID playerId = UUID.randomUUID();

        sanctuaryRepository.save(sanctuary);
        trustRepository.addTrusted(sanctuary.id(), playerId, Instant.now());
        trustRepository.setCapability(sanctuary.id(), playerId, SanctuaryCapability.BUILD, true);

        sanctuaryRepository.delete(sanctuary.id());

        assertFalse(trustRepository.isTrusted(sanctuary.id(), playerId));
        assertTrue(trustRepository.findCapabilities(sanctuary.id(), playerId).isEmpty());
    }

    private static Sanctuary sanctuary() {
        Instant now = Instant.parse("2026-08-22T12:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            "Test Sanctuary",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            18.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }
}

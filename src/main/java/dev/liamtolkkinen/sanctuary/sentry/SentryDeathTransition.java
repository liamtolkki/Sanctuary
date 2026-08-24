package dev.liamtolkkinen.sanctuary.sentry;

import java.util.Optional;

/** Builds the persistence view used when a sentry entity is already in its vanilla death sequence. */
final class SentryDeathTransition {
    private SentryDeathTransition() {
    }

    static SentryRecord withoutEntity(SentryRecord record) {
        return new SentryRecord(
            record.id(),
            record.sanctuaryId(),
            record.itemId(),
            record.world(),
            record.x(),
            record.y(),
            record.z(),
            Optional.empty(),
            record.state(),
            record.respawnAt(),
            record.recallDeadline(),
            record.createdAt(),
            record.updatedAt()
        );
    }
}

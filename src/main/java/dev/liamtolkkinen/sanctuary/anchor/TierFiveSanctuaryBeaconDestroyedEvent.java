package dev.liamtolkkinen.sanctuary.anchor;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired when a bound Tier V Sanctuary Beacon item is permanently destroyed. */
public final class TierFiveSanctuaryBeaconDestroyedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ownerId;

    public TierFiveSanctuaryBeaconDestroyedEvent(UUID ownerId) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    }

    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

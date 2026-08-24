package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after an anchor placement successfully creates a new Sanctuary. */
public final class SanctuaryCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Sanctuary sanctuary;

    public SanctuaryCreatedEvent(Player player, Sanctuary sanctuary) {
        this.player = Objects.requireNonNull(player, "player");
        this.sanctuary = Objects.requireNonNull(sanctuary, "sanctuary");
    }

    public Player player() {
        return player;
    }

    public Sanctuary sanctuary() {
        return sanctuary;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

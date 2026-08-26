package dev.liamtolkkinen.sanctuary.anchor;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after an active Sanctuary anchor tier upgrade is fully persisted. */
public final class AnchorTierUpgradedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SanctuaryAnchor anchor;

    public AnchorTierUpgradedEvent(Player player, SanctuaryAnchor anchor) {
        this.player = Objects.requireNonNull(player, "player");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    public Player player() {
        return player;
    }

    public SanctuaryAnchor anchor() {
        return anchor;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

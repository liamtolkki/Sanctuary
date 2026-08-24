package dev.liamtolkkinen.sanctuary.ui;

import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorMetadata;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/** Opens anchor-specific management while preserving shared Sanctuary state. */
public final class SanctuaryUiListener implements Listener {
    private final AnchorItemService anchorItemService;
    private final SanctuaryAnchorRepository anchorRepository;
    private final SanctuaryRepository sanctuaryRepository;
    private final AnchorUiService anchorUiService;
    private final Logger logger;

    public SanctuaryUiListener(
        AnchorItemService anchorItemService,
        SanctuaryAnchorRepository anchorRepository,
        SanctuaryRepository sanctuaryRepository,
        AnchorUiService anchorUiService,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.anchorRepository = anchorRepository;
        this.sanctuaryRepository = sanctuaryRepository;
        this.anchorUiService = anchorUiService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onAnchorInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof TileState tileState)) {
            return;
        }

        Optional<AnchorMetadata> metadataResult = anchorItemService.readBlockMetadata(tileState);
        if (metadataResult.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        try {
            SanctuaryAnchor anchor = anchorRepository.findById(
                metadataResult.orElseThrow().anchorId()
            ).orElse(null);
            if (anchor == null) {
                if (player.hasPermission("sanctuary.admin")) {
                    player.sendMessage(
                        ChatColor.RED + "This Sanctuary anchor has no matching graph record. "
                            + "Break it to clean up the orphan."
                    );
                }
                return;
            }
            Sanctuary sanctuary = sanctuaryRepository.findById(anchor.sanctuaryId()).orElse(null);
            if (sanctuary == null) {
                if (player.hasPermission("sanctuary.admin")) {
                    player.sendMessage(ChatColor.RED + "This anchor's Sanctuary record is missing.");
                }
                return;
            }

            boolean owner = sanctuary.ownerId().equals(player.getUniqueId());
            boolean admin = player.hasPermission("sanctuary.admin");
            if (!owner && !admin) {
                return;
            }

            event.setCancelled(true);
            boolean adminMode = admin && (!owner || player.isSneaking());
            anchorUiService.open(player, sanctuary, anchor, adminMode);
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not open this anchor UI.");
            logger.log(Level.SEVERE, "Failed to resolve clicked Sanctuary anchor", exception);
        }
    }
}

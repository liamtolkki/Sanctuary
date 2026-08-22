package dev.liamtolkkinen.sanctuary.ui;

import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorMetadata;
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

/** Opens Sanctuary management through ExtendedUI when a registered anchor is clicked. */
public final class SanctuaryUiListener implements Listener {
    private final AnchorItemService anchorItemService;
    private final SanctuaryRepository repository;
    private final SanctuaryUiService uiService;
    private final Logger logger;

    public SanctuaryUiListener(
        AnchorItemService anchorItemService,
        SanctuaryRepository repository,
        SanctuaryUiService uiService,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.repository = repository;
        this.uiService = uiService;
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
            Sanctuary sanctuary = repository.findById(metadataResult.orElseThrow().anchorId()).orElse(null);
            if (sanctuary == null) {
                if (player.hasPermission("sanctuary.admin")) {
                    player.sendMessage(
                        ChatColor.RED
                            + "This Sanctuary anchor has no matching database record. Break it to clean up the orphan."
                    );
                }
                return;
            }

            boolean owner = sanctuary.ownerId().equals(player.getUniqueId());
            boolean admin = player.hasPermission("sanctuary.admin");
            if (!owner && !admin) {
                return;
            }

            event.setCancelled(true);
            if (admin && (!owner || player.isSneaking())) {
                uiService.openAdmin(player, sanctuary);
            } else {
                uiService.openPersonal(player, sanctuary);
            }
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not open this anchor UI.");
            logger.log(Level.SEVERE, "Failed to resolve clicked Sanctuary anchor", exception);
        }
    }
}

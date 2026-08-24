package dev.liamtolkkinen.sanctuary.ui;

import dev.liamtolkkinen.extendedui.ExtendedTextInputDialog;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorMetadata;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryCreatedEvent;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
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

    @EventHandler
    public void onSanctuaryCreated(SanctuaryCreatedEvent event) {
        Player player = event.player();
        Sanctuary sanctuary = event.sanctuary();
        if (!sanctuary.ownerId().equals(player.getUniqueId())) {
            return;
        }

        ExtendedTextInputDialog dialog = ExtendedTextInputDialog.builder(
                Component.text("Set Sanctuary Name"),
                Component.text("Sanctuary name")
            )
            .initialValue(sanctuary.name())
            .maxLength(32)
            .confirmText(Component.text("Set Name"))
            .cancelText(Component.text("Cancel"))
            .onConfirm((callbackPlayer, value) -> setInitialName(callbackPlayer, sanctuary.id(), value))
            .build();
        dialog.show(player);
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

    private void setInitialName(Player player, java.util.UUID sanctuaryId, String requestedName) {
        final String name;
        try {
            name = SanctuaryUiService.normalizeSanctuaryName(requestedName);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            return;
        }

        try {
            Sanctuary current = sanctuaryRepository.findById(sanctuaryId).orElse(null);
            if (current == null) {
                player.sendMessage(ChatColor.RED + "That Sanctuary no longer exists.");
                return;
            }
            if (!current.ownerId().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Only the Sanctuary owner can rename it.");
                return;
            }

            sanctuaryRepository.save(new Sanctuary(
                current.id(),
                current.ownerId(),
                current.type(),
                name,
                current.position(),
                current.tier(),
                current.anchorGeneration(),
                current.territoryRadius(),
                current.state(),
                current.destroyedAt(),
                current.destructionReason(),
                current.debugEphemeral(),
                current.createdAt(),
                Instant.now()
            ));
            player.sendMessage(ChatColor.GREEN + "Sanctuary named " + name + ".");
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not save that name.");
            logger.log(Level.SEVERE, "Failed to set initial Sanctuary name " + sanctuaryId, exception);
        }
    }
}

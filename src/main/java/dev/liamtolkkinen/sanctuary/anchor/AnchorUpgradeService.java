package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Performs exact-item, per-anchor Sanctuary tier upgrades. */
public final class AnchorUpgradeService {
    private final JavaPlugin plugin;
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuaryAnchorRepository anchorRepository;
    private final AnchorItemService anchorItemService;

    public AnchorUpgradeService(
        JavaPlugin plugin,
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        AnchorItemService anchorItemService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sanctuaryRepository = Objects.requireNonNull(sanctuaryRepository, "sanctuaryRepository");
        this.anchorRepository = Objects.requireNonNull(anchorRepository, "anchorRepository");
        this.anchorItemService = Objects.requireNonNull(anchorItemService, "anchorItemService");
    }

    public SanctuaryAnchor upgrade(
        Player player,
        UUID anchorId,
        double maximumRadius,
        boolean adminMode
    ) throws SQLException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(anchorId, "anchorId");

        SanctuaryAnchor anchor = anchorRepository.findById(anchorId)
            .orElseThrow(() -> new IllegalStateException("That Sanctuary anchor no longer exists."));
        Sanctuary sanctuary = sanctuaryRepository.findById(anchor.sanctuaryId())
            .orElseThrow(() -> new IllegalStateException("That anchor's Sanctuary no longer exists."));

        if (!adminMode && !sanctuary.ownerId().equals(player.getUniqueId())) {
            throw new IllegalStateException("Only the Sanctuary owner may upgrade this anchor.");
        }
        if (anchor.state() != SanctuaryState.ACTIVE || anchor.position().isEmpty()) {
            throw new IllegalStateException("Only an active placed Sanctuary anchor can be upgraded.");
        }
        if (anchor.tier() >= AnchorTierProgression.MAX_TIER) {
            throw new IllegalStateException("This Sanctuary anchor is already Tier V.");
        }

        ExtendedItemId requiredItem = AnchorTierProgression.requiredUpgradeItem(anchor.tier());
        if (!hasExactItem(player.getInventory(), requiredItem)) {
            throw new IllegalStateException(
                "You need a " + displayName(requiredItem) + " to upgrade this anchor."
            );
        }

        TileState tileState = requirePlacedAnchorState(anchor);
        AnchorMetadata blockMetadata = anchorItemService.readBlockMetadata(tileState)
            .orElseThrow(() -> new IllegalStateException(
                "The placed anchor is missing its Sanctuary metadata."
            ));
        if (!blockMetadata.anchorId().equals(anchor.id())
            || blockMetadata.generation() != anchor.generation()
            || blockMetadata.tier() != anchor.tier()) {
            throw new IllegalStateException(
                "The placed anchor metadata does not match the registered anchor."
            );
        }

        int nextTier = AnchorTierProgression.nextTier(anchor.tier());
        double nextRadius = AnchorTierProgression.radiusForTier(maximumRadius, nextTier);
        Instant now = Instant.now();
        SanctuaryAnchor upgraded = new SanctuaryAnchor(
            anchor.id(),
            anchor.sanctuaryId(),
            anchor.parentAnchorId(),
            anchor.type(),
            anchor.position(),
            nextTier,
            anchor.generation(),
            nextRadius,
            anchor.state(),
            anchor.destroyedAt(),
            anchor.destructionReason(),
            anchor.createdAt(),
            now
        );

        anchorRepository.save(upgraded);
        try {
            anchorItemService.writeBlockMetadata(
                tileState,
                new AnchorMetadata(
                    upgraded.id(),
                    Optional.of(sanctuary.ownerId()),
                    upgraded.tier(),
                    upgraded.generation()
                )
            );
            synchronizeCompatibilitySummary(sanctuary, upgraded, now);
        } catch (SQLException | RuntimeException exception) {
            anchorRepository.save(anchor);
            throw exception;
        }

        if (!consumeExactItem(player.getInventory(), requiredItem)) {
            anchorRepository.save(anchor);
            anchorItemService.writeBlockMetadata(tileState, blockMetadata);
            synchronizeCompatibilitySummary(sanctuary, anchor, Instant.now());
            throw new IllegalStateException(
                "The required upgrade item disappeared before the upgrade could finish."
            );
        }

        Bukkit.getPluginManager().callEvent(new AnchorTierUpgradedEvent(player, upgraded));
        return upgraded;
    }

    private TileState requirePlacedAnchorState(SanctuaryAnchor anchor) {
        var position = anchor.position().orElseThrow();
        World world = Bukkit.getWorld(position.world());
        if (world == null) {
            throw new IllegalStateException("The anchor's world is not currently available.");
        }
        BlockState state = world.getBlockAt(position.x(), position.y(), position.z()).getState();
        if (!(state instanceof TileState tileState)) {
            throw new IllegalStateException("The registered anchor block is no longer present.");
        }
        return tileState;
    }

    private void synchronizeCompatibilitySummary(
        Sanctuary sanctuary,
        SanctuaryAnchor changedAnchor,
        Instant now
    ) throws SQLException {
        List<SanctuaryAnchor> anchors = anchorRepository.findBySanctuary(sanctuary.id()).stream()
            .map(anchor -> anchor.id().equals(changedAnchor.id()) ? changedAnchor : anchor)
            .filter(anchor -> anchor.state() != SanctuaryState.DESTROYED)
            .toList();
        int summaryTier = anchors.stream()
            .mapToInt(SanctuaryAnchor::tier)
            .max()
            .orElse(changedAnchor.tier());
        double summaryRadius = anchors.stream()
            .mapToDouble(SanctuaryAnchor::territoryRadius)
            .max()
            .orElse(changedAnchor.territoryRadius());

        sanctuaryRepository.save(new Sanctuary(
            sanctuary.id(),
            sanctuary.ownerId(),
            sanctuary.type(),
            sanctuary.name(),
            sanctuary.position(),
            summaryTier,
            sanctuary.anchorGeneration(),
            summaryRadius,
            sanctuary.state(),
            sanctuary.destroyedAt(),
            sanctuary.destructionReason(),
            sanctuary.debugEphemeral(),
            sanctuary.createdAt(),
            now
        ));
    }

    private static boolean hasExactItem(PlayerInventory inventory, ExtendedItemId itemId) {
        for (ItemStack item : inventory.getStorageContents()) {
            if (ExtendedItems.is(item, itemId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeExactItem(PlayerInventory inventory, ExtendedItemId itemId) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (!ExtendedItems.is(item, itemId)) {
                continue;
            }
            if (item.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, item);
            }
            return true;
        }
        return false;
    }

    private static String displayName(ExtendedItemId itemId) {
        String[] words = itemId.persistentId().split("_");
        StringBuilder value = new StringBuilder();
        for (String word : words) {
            if (!value.isEmpty()) {
                value.append(' ');
            }
            value.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return value.toString();
    }
}

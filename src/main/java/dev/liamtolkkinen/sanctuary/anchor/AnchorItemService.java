package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.TileState;
import org.bukkit.plugin.Plugin;

public final class AnchorItemService {
    private final NamespacedKey anchorIdKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey tierKey;

    public AnchorItemService(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        anchorIdKey = new NamespacedKey(plugin, "anchor_id");
        ownerUuidKey = new NamespacedKey(plugin, "owner_uuid");
        tierKey = new NamespacedKey(plugin, "tier");
    }

    public ItemStack createUnboundBeacon() {
        ItemStack item = ExtendedItems.create(ExtendedItemIds.SANCTUARY_BEACON);
        writeItemMetadata(
            item,
            new AnchorMetadata(
                UUID.randomUUID(),
                Optional.empty(),
                1
            )
        );

        if (!ExtendedItems.validate(item).isValid()) {
            throw new IllegalStateException(
                "ExtendedItems rejected a Sanctuary Beacon after Sanctuary metadata was added"
            );
        }

        return item;
    }

    public boolean isSanctuaryBeacon(ItemStack item) {
        return item != null
            && ExtendedItems.is(item, ExtendedItemIds.SANCTUARY_BEACON);
    }

    public boolean isSanctuaryConduit(ItemStack item) {
        return item != null
            && ExtendedItems.is(item, ExtendedItemIds.SANCTUARY_CONDUIT);
    }

    public Optional<AnchorMetadata> readBeacon(ItemStack item) {
        if (!isSanctuaryBeacon(item)) {
            return Optional.empty();
        }
        if (!ExtendedItems.validate(item).isValid()) {
            return Optional.empty();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        return read(meta.getPersistentDataContainer());
    }

    public void writeItemMetadata(ItemStack item, AnchorMetadata metadata) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(metadata, "metadata");

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("item has no ItemMeta");
        }

        write(meta.getPersistentDataContainer(), metadata);
        item.setItemMeta(meta);
    }

    public Optional<AnchorMetadata> readBlockMetadata(TileState tileState) {
        Objects.requireNonNull(tileState, "tileState");
        return read(tileState.getPersistentDataContainer());
    }

    public void writeBlockMetadata(TileState tileState, AnchorMetadata metadata) {
        Objects.requireNonNull(tileState, "tileState");
        Objects.requireNonNull(metadata, "metadata");

        write(tileState.getPersistentDataContainer(), metadata);
        if (!tileState.update(true, false)) {
            throw new IllegalStateException("failed to persist Sanctuary metadata to placed anchor");
        }
    }

    private Optional<AnchorMetadata> read(PersistentDataContainer data) {
        if (!data.has(anchorIdKey, PersistentDataType.STRING)) {
            return Optional.empty();
        }
        if (!data.has(tierKey, PersistentDataType.INTEGER)) {
            return Optional.empty();
        }
        if (data.getKeys().contains(ownerUuidKey)
            && !data.has(ownerUuidKey, PersistentDataType.STRING)) {
            return Optional.empty();
        }

        String anchorIdValue = data.get(anchorIdKey, PersistentDataType.STRING);
        Integer tierValue = data.get(tierKey, PersistentDataType.INTEGER);
        String ownerIdValue = data.get(ownerUuidKey, PersistentDataType.STRING);

        if (anchorIdValue == null || tierValue == null || tierValue < 1) {
            return Optional.empty();
        }

        try {
            UUID anchorId = UUID.fromString(anchorIdValue);
            Optional<UUID> ownerId = ownerIdValue == null
                ? Optional.empty()
                : Optional.of(UUID.fromString(ownerIdValue));

            return Optional.of(new AnchorMetadata(anchorId, ownerId, tierValue));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void write(PersistentDataContainer data, AnchorMetadata metadata) {
        data.set(
            anchorIdKey,
            PersistentDataType.STRING,
            metadata.anchorId().toString()
        );
        data.set(
            tierKey,
            PersistentDataType.INTEGER,
            metadata.tier()
        );

        if (metadata.ownerId().isPresent()) {
            data.set(
                ownerUuidKey,
                PersistentDataType.STRING,
                metadata.ownerId().orElseThrow().toString()
            );
        } else {
            data.remove(ownerUuidKey);
        }
    }
}

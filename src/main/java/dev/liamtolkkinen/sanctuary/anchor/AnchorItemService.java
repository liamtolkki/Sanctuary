package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class AnchorItemService {
    private static final int LEGACY_GENERATION = 1;

    private final NamespacedKey anchorIdKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey generationKey;

    public AnchorItemService(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        anchorIdKey = new NamespacedKey(plugin, "anchor_id");
        ownerUuidKey = new NamespacedKey(plugin, "owner_uuid");
        tierKey = new NamespacedKey(plugin, "tier");
        generationKey = new NamespacedKey(plugin, "generation");
    }

    public ItemStack createUnboundBeacon() {
        return createBeacon(new AnchorMetadata(
            UUID.randomUUID(),
            Optional.empty(),
            1,
            1
        ));
    }

    public ItemStack createBoundBeacon(AnchorMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        if (!metadata.isBound()) {
            throw new IllegalArgumentException("bound Sanctuary Beacon metadata must have an owner");
        }
        return createBeacon(metadata);
    }

    public ItemStack createBoundBeacon(Sanctuary sanctuary) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        return createBoundBeacon(new AnchorMetadata(
            sanctuary.id(),
            Optional.of(sanctuary.ownerId()),
            sanctuary.tier(),
            sanctuary.anchorGeneration()
        ));
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

    private ItemStack createBeacon(AnchorMetadata metadata) {
        ItemStack item = ExtendedItems.create(ExtendedItemIds.SANCTUARY_BEACON);
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        writeItemMetadata(item, metadata);

        if (!ExtendedItems.validate(item).isValid()) {
            throw new IllegalStateException(
                "ExtendedItems rejected a Sanctuary Beacon after Sanctuary metadata was added"
            );
        }

        return item;
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
        if (data.getKeys().contains(generationKey)
            && !data.has(generationKey, PersistentDataType.INTEGER)) {
            return Optional.empty();
        }

        String anchorIdValue = data.get(anchorIdKey, PersistentDataType.STRING);
        Integer tierValue = data.get(tierKey, PersistentDataType.INTEGER);
        String ownerIdValue = data.get(ownerUuidKey, PersistentDataType.STRING);
        Integer generationValue = data.get(generationKey, PersistentDataType.INTEGER);

        if (anchorIdValue == null || tierValue == null || tierValue < 1) {
            return Optional.empty();
        }

        int generation = generationValue == null ? LEGACY_GENERATION : generationValue;
        if (generation < 1) {
            return Optional.empty();
        }

        try {
            UUID anchorId = UUID.fromString(anchorIdValue);
            Optional<UUID> ownerId = ownerIdValue == null
                ? Optional.empty()
                : Optional.of(UUID.fromString(ownerIdValue));

            return Optional.of(new AnchorMetadata(anchorId, ownerId, tierValue, generation));
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
        data.set(
            generationKey,
            PersistentDataType.INTEGER,
            metadata.generation()
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

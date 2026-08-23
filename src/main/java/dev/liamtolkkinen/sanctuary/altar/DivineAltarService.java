package dev.liamtolkkinen.sanctuary.altar;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedItemBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.extendedui.StandardButtons;
import dev.liamtolkkinen.sanctuary.crafting.SanctuaryRecipeCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns placed Divine Altar state, effects, destruction safety, and altar menus. */
public final class DivineAltarService implements Listener, AutoCloseable {
    private static final byte MARKER_VALUE = 1;
    private static final int[] RECIPE_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };
    private static final List<ExtendedItemId> OFFERINGS = List.of(
        ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT,
        ExtendedItemIds.CONSECRATED_SHARD,
        ExtendedItemIds.WATCHERS_EYE,
        ExtendedItemIds.WARD_STONE,
        ExtendedItemIds.BLAST_WARD,
        ExtendedItemIds.GUARDIAN_TOKEN,
        ExtendedItemIds.PURIFICATION_RELIC,
        ExtendedItemIds.TERRITORY_KEYSTONE,
        ExtendedItemIds.SEAL_OF_KEEPING,
        ExtendedItemIds.SENTINEL_SEAL,
        ExtendedItemIds.SANCTUARY_CORE,
        ExtendedItemIds.CONSECRATED_KEYSTONE
    );

    private final JavaPlugin plugin;
    private final ExtendedUI ui;
    private final NamespacedKey altarKey;
    private final Set<BlockPosition> loadedAltars = ConcurrentHashMap.newKeySet();
    private BukkitTask particleTask;

    public DivineAltarService(JavaPlugin plugin, ExtendedUI ui) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.altarKey = new NamespacedKey(plugin, "divine_altar");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                discoverAltars(chunk);
            }
        }
        particleTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::spawnParticles,
            10L,
            10L
        );
    }

    @Override
    public void close() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        loadedAltars.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ExtendedItems.is(event.getItemInHand(), ExtendedItemIds.DIVINE_ALTAR)) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof TileState tileState)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("The Divine Altar could not bind to that block.");
            return;
        }
        tileState.getPersistentDataContainer().set(altarKey, PersistentDataType.BYTE, MARKER_VALUE);
        tileState.update(true, false);
        loadedAltars.add(BlockPosition.of(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (!isAltar(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        ui.open(event.getPlayer(), new AltarHomeMenu());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isAltar(event.getBlock())) {
            return;
        }
        loadedAltars.remove(BlockPosition.of(event.getBlock()));
        event.setDropItems(false);
        event.setExpToDrop(0);
        dropAltar(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        replaceExplosionDrops(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        replaceExplosionDrops(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isAltar(block)) {
                event.setCancelled(true);
                breakAltarImmediately(block);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isAltar(block)) {
                event.setCancelled(true);
                breakAltarImmediately(block);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        discoverAltars(event.getChunk());
    }

    private void replaceExplosionDrops(List<Block> blocks) {
        for (Block block : List.copyOf(blocks)) {
            if (!isAltar(block)) {
                continue;
            }
            loadedAltars.remove(BlockPosition.of(block));
            blocks.remove(block);
            breakAltarImmediately(block);
        }
    }

    private void breakAltarImmediately(Block block) {
        loadedAltars.remove(BlockPosition.of(block));
        Location location = block.getLocation();
        block.setType(Material.AIR, false);
        dropAltar(location);
    }

    private void dropAltar(Location location) {
        location.getWorld().dropItemNaturally(
            location.clone().add(0.5, 0.5, 0.5),
            ExtendedItems.create(ExtendedItemIds.DIVINE_ALTAR)
        );
    }

    private boolean isAltar(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) {
            return false;
        }
        Byte marker = tileState.getPersistentDataContainer().get(altarKey, PersistentDataType.BYTE);
        return marker != null && marker == MARKER_VALUE;
    }

    private void discoverAltars(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof TileState tileState)) {
                continue;
            }
            Byte marker = tileState.getPersistentDataContainer().get(altarKey, PersistentDataType.BYTE);
            if (marker != null && marker == MARKER_VALUE) {
                loadedAltars.add(BlockPosition.of(state.getBlock()));
            }
        }
    }

    private void spawnParticles() {
        for (BlockPosition position : List.copyOf(loadedAltars)) {
            World world = Bukkit.getWorld(position.worldId());
            if (world == null || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (!isAltar(block)) {
                loadedAltars.remove(position);
                continue;
            }
            Location center = block.getLocation().add(0.5, 1.05, 0.5);
            world.spawnParticle(Particle.FIREFLY, center, 2, 0.55, 0.35, 0.55, 0.01);
            if (world.getGameTime() % 20L == 0L) {
                world.spawnParticle(Particle.ENCHANT, center, 4, 0.4, 0.15, 0.4, 0.15);
            }
        }
    }

    private final class AltarHomeMenu extends ExtendedInventoryMenu {
        private AltarHomeMenu() {
            super(3, "<light_purple>Divine Altar");
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            menu.set(11, menuButton(
                Material.ENCHANTED_BOOK,
                "<aqua>Sacred Arts",
                "<gray>Browse Sanctuary recipes",
                click -> click.menu().open(new SacredArtsMenu())
            ));
            menu.set(13, menuButton(
                Material.ECHO_SHARD,
                "<light_purple>Offerings",
                "<gray>View the twelve sacred offerings",
                click -> click.menu().open(new OfferingsMenu())
            ));
            menu.set(15, menuButton(
                Material.NETHER_STAR,
                "<gold>Divine Favor",
                "<gray>View the path to the Divine Relic",
                click -> click.menu().open(new DivineFavorMenu())
            ));
            menu.set(22, StandardButtons.close(context.theme()));
        }
    }

    private final class SacredArtsMenu extends ExtendedInventoryMenu {
        private SacredArtsMenu() {
            super(5, "<aqua>Sacred Arts");
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            List<SanctuaryRecipeCatalog.RecipeDefinition> recipes = SanctuaryRecipeCatalog.allRecipes();
            for (int index = 0; index < recipes.size() && index < RECIPE_SLOTS.length; index++) {
                var recipe = recipes.get(index);
                menu.set(RECIPE_SLOTS[index], ExtendedButton.builder(() -> recipeResultIcon(recipe))
                    .onClick(click -> click.menu().open(new RecipeDetailMenu(recipe)))
                    .build());
            }
            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private final class RecipeDetailMenu extends ExtendedInventoryMenu {
        private final SanctuaryRecipeCatalog.RecipeDefinition recipe;

        private RecipeDetailMenu(SanctuaryRecipeCatalog.RecipeDefinition recipe) {
            super(5, "<aqua>Sacred Recipe");
            this.recipe = recipe;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Player player = context.player();
            ItemStack[] displayed = recipeGrid(recipe);
            int[] gridSlots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
            for (int i = 0; i < displayed.length; i++) {
                if (displayed[i] != null) {
                    menu.set(gridSlots[i], ExtendedButton.builder(displayed[i]).enabled(false).build());
                }
            }

            boolean ready = hasAllIngredients(player, recipe);
            menu.set(24, ExtendedButton.builder(() -> recipeStatusIcon(player, recipe, ready)).enabled(false).build());
            menu.set(25, ExtendedButton.builder(() -> recipeResultIcon(recipe)).enabled(false).build());
            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private final class OfferingsMenu extends ExtendedInventoryMenu {
        private OfferingsMenu() {
            super(5, "<light_purple>Sacred Offerings");
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            for (int i = 0; i < OFFERINGS.size(); i++) {
                int slot = RECIPE_SLOTS[i];
                int number = i + 1;
                ExtendedItemId id = OFFERINGS.get(i);
                menu.set(slot, ExtendedButton.builder(() -> offeringIcon(number, id)).enabled(false).build());
            }
            menu.set(31, ExtendedButton.builder(() -> ExtendedItemBuilder.of(Material.WRITABLE_BOOK)
                .name("<yellow>Offering ritual not active yet")
                .lore(
                    "<gray>The altar now knows the agreed offering order.",
                    "<gray>Persistent sacrifice progress has not been implemented yet."
                )
                .build()).enabled(false).build());
            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private final class DivineFavorMenu extends ExtendedInventoryMenu {
        private DivineFavorMenu() {
            super(3, "<gold>Divine Favor");
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            menu.set(13, ExtendedButton.builder(() -> ExtendedItemBuilder.of(Material.NETHER_STAR)
                .name("<gold>Divine Relic")
                .lore(
                    "<gray>Complete all twelve offerings to earn divine favor.",
                    "<yellow>Offering progress persistence is the next altar step."
                )
                .glint(true)
                .build()).enabled(false).build());
            menu.set(18, StandardButtons.back(context.theme()));
            menu.set(26, StandardButtons.close(context.theme()));
        }
    }

    private ExtendedButton menuButton(
        Material material,
        String name,
        String lore,
        java.util.function.Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> click
    ) {
        return ExtendedButton.builder(() -> ExtendedItemBuilder.of(material)
                .name(name)
                .lore(lore)
                .build())
            .onClick(click)
            .build();
    }

    private ItemStack recipeResultIcon(SanctuaryRecipeCatalog.RecipeDefinition recipe) {
        ItemStack item = ExtendedItems.create(recipe.result());
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.text("Click to view recipe", NamedTextColor.AQUA));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack[] recipeGrid(SanctuaryRecipeCatalog.RecipeDefinition recipe) {
        ItemStack[] grid = new ItemStack[9];
        if (recipe instanceof SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    char symbol = shaped.shape().get(row).charAt(column);
                    if (symbol != ' ') {
                        grid[row * 3 + column] = ingredientIcon(shaped.ingredients().get(symbol));
                    }
                }
            }
            return grid;
        }
        var shapeless = (SanctuaryRecipeCatalog.ShapelessRecipeDefinition) recipe;
        for (int i = 0; i < shapeless.ingredients().size(); i++) {
            grid[i] = ingredientIcon(shapeless.ingredients().get(i));
        }
        return grid;
    }

    private ItemStack ingredientIcon(SanctuaryRecipeCatalog.Ingredient ingredient) {
        return ingredient.extendedItem() != null
            ? ExtendedItems.create(ingredient.extendedItem())
            : new ItemStack(ingredient.material());
    }

    private ItemStack recipeStatusIcon(
        Player player,
        SanctuaryRecipeCatalog.RecipeDefinition recipe,
        boolean ready
    ) {
        List<SanctuaryRecipeCatalog.Ingredient> required = flattenedIngredients(recipe);
        Map<String, Integer> needed = new LinkedHashMap<>();
        Map<String, Integer> owned = new LinkedHashMap<>();
        Map<String, SanctuaryRecipeCatalog.Ingredient> definitions = new LinkedHashMap<>();
        for (var ingredient : required) {
            String key = ingredientKey(ingredient);
            needed.merge(key, 1, Integer::sum);
            definitions.putIfAbsent(key, ingredient);
        }
        for (var entry : definitions.entrySet()) {
            owned.put(entry.getKey(), countIngredient(player, entry.getValue()));
        }

        List<Component> lore = new ArrayList<>();
        for (var entry : needed.entrySet()) {
            int have = owned.getOrDefault(entry.getKey(), 0);
            int need = entry.getValue();
            NamedTextColor color = have >= need ? NamedTextColor.GREEN : NamedTextColor.RED;
            lore.add(Component.text(displayName(definitions.get(entry.getKey())) + ": " + have + " / " + need, color));
        }
        lore.add(Component.empty());
        lore.add(Component.text(
            ready ? "You have every exact ingredient." : "Missing exact Sanctuary ingredients.",
            ready ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));

        ItemStack status = new ItemStack(ready ? Material.LIME_DYE : Material.GRAY_DYE);
        status.editMeta(meta -> {
            meta.displayName(Component.text(ready ? "READY" : "NOT READY", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            meta.lore(lore);
        });
        return status;
    }

    private ItemStack offeringIcon(int number, ExtendedItemId id) {
        ItemStack item = ExtendedItems.create(id);
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.text("Offering " + number + " of 12", NamedTextColor.LIGHT_PURPLE));
            meta.lore(lore);
        });
        return item;
    }

    private boolean hasAllIngredients(Player player, SanctuaryRecipeCatalog.RecipeDefinition recipe) {
        Map<String, Integer> required = new LinkedHashMap<>();
        Map<String, SanctuaryRecipeCatalog.Ingredient> definitions = new LinkedHashMap<>();
        for (var ingredient : flattenedIngredients(recipe)) {
            String key = ingredientKey(ingredient);
            required.merge(key, 1, Integer::sum);
            definitions.putIfAbsent(key, ingredient);
        }
        for (var entry : required.entrySet()) {
            if (countIngredient(player, definitions.get(entry.getKey())) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private List<SanctuaryRecipeCatalog.Ingredient> flattenedIngredients(
        SanctuaryRecipeCatalog.RecipeDefinition recipe
    ) {
        List<SanctuaryRecipeCatalog.Ingredient> result = new ArrayList<>();
        if (recipe instanceof SanctuaryRecipeCatalog.ShapedRecipeDefinition shaped) {
            for (String row : shaped.shape()) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol != ' ') {
                        result.add(shaped.ingredients().get(symbol));
                    }
                }
            }
        } else {
            result.addAll(((SanctuaryRecipeCatalog.ShapelessRecipeDefinition) recipe).ingredients());
        }
        return result;
    }

    private int countIngredient(Player player, SanctuaryRecipeCatalog.Ingredient ingredient) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (ingredient.extendedItem() != null) {
                if (ExtendedItems.is(item, ingredient.extendedItem())) {
                    count += item.getAmount();
                }
            } else if (item.getType() == ingredient.material() && ExtendedItems.getId(item).isEmpty()) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private String ingredientKey(SanctuaryRecipeCatalog.Ingredient ingredient) {
        return ingredient.extendedItem() != null
            ? "extended:" + ingredient.extendedItem().persistentId()
            : "material:" + ingredient.material().getKey();
    }

    private String displayName(SanctuaryRecipeCatalog.Ingredient ingredient) {
        if (ingredient.extendedItem() != null) {
            ItemStack item = ExtendedItems.create(ingredient.extendedItem());
            Component name = item.getItemMeta().displayName();
            return name == null ? pretty(ingredient.extendedItem().persistentId()) : pretty(ingredient.extendedItem().persistentId());
        }
        return pretty(ingredient.material().name());
    }

    private String pretty(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record BlockPosition(UUID worldId, int x, int y, int z) {
        private static BlockPosition of(Block block) {
            return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}

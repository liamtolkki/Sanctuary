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
import dev.liamtolkkinen.sanctuary.advancement.SanctuaryAdvancementService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.crafting.SanctuaryRecipeCatalog;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns placed Divine Altar state, effects, crafting, offerings, and destruction safety. */
public final class DivineAltarService implements Listener, AutoCloseable {
    private static final byte MARKER_VALUE = 1;
    private static final int[] RECIPE_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };

    private final JavaPlugin plugin;
    private final ExtendedUI ui;
    private final NamespacedKey altarKey;
    private final AnchorItemService anchorItemService;
    private final SanctuaryAdvancementService advancementService;
    private final AltarGuardCraftingMenus guardCraftingMenus;
    private final OfferingProgressRepository offeringProgress;
    private final Set<BlockPosition> loadedAltars = ConcurrentHashMap.newKeySet();
    private BukkitTask particleTask;

    public DivineAltarService(
        JavaPlugin plugin,
        ExtendedUI ui,
        OfferingProgressRepository offeringProgress
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.offeringProgress = Objects.requireNonNull(offeringProgress, "offeringProgress");
        this.altarKey = new NamespacedKey(plugin, "divine_altar");
        this.anchorItemService = new AnchorItemService(plugin);
        this.advancementService = new SanctuaryAdvancementService(plugin);
        this.guardCraftingMenus = new AltarGuardCraftingMenus(plugin, advancementService);
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
            event.getPlayer().sendMessage("The Divine Altar could not bind to that block.");
            return;
        }
        tileState.getPersistentDataContainer().set(altarKey, PersistentDataType.BYTE, MARKER_VALUE);
        tileState.update(true, false);
        loadedAltars.add(BlockPosition.of(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null) {
            return;
        }
        if (!isAltar(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        awardPendingDivineRelic(event.getPlayer());
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
    public void onBurn(BlockBurnEvent event) {
        if (!isAltar(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        breakAltarImmediately(event.getBlock());
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
        breakIfPistonMovesAltar(event.getBlocks(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        breakIfPistonMovesAltar(event.getBlocks(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        discoverAltars(event.getChunk());
    }

    private void breakIfPistonMovesAltar(List<Block> blocks, java.util.function.Consumer<Boolean> cancel) {
        for (Block block : blocks) {
            if (!isAltar(block)) {
                continue;
            }
            cancel.accept(true);
            breakAltarImmediately(block);
            return;
        }
    }

    private void replaceExplosionDrops(List<Block> blocks) {
        for (Block block : List.copyOf(blocks)) {
            if (!isAltar(block)) {
                continue;
            }
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
            customItem(ExtendedItemIds.DIVINE_ALTAR)
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

            Location base = block.getLocation().add(0.5, 0.0, 0.5);
            Location upper = base.clone().add(0.0, 1.05, 0.0);
            world.spawnParticle(Particle.FIREFLY, upper, 2, 0.55, 0.35, 0.55, 0.01);

            double angle = (world.getGameTime() % 80L) * (Math.PI * 2.0 / 80.0);
            double radius = 0.62;
            for (int i = 0; i < 2; i++) {
                double current = angle + (Math.PI * i);
                Location point = base.clone().add(
                    Math.cos(current) * radius,
                    0.22,
                    Math.sin(current) * radius
                );
                world.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.04, 0.02, 0.005);
            }

            if (world.getGameTime() % 20L == 0L) {
                world.spawnParticle(Particle.ENCHANT, upper, 4, 0.4, 0.15, 0.4, 0.15);
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
            menu.set(10, menuButton(
                Material.ENCHANTED_BOOK,
                "<aqua>Sacred Arts",
                "<gray>Browse and craft Sanctuary recipes",
                click -> click.menu().open(new SacredArtsMenu())
            ));
            menu.set(12, customMenuButton(
                ExtendedItemIds.COMPANION_WARDEN,
                "<aqua>Companions",
                "<gray>Craft companion spawn eggs",
                click -> click.menu().open(guardCraftingMenus.companionsMenu())
            ));
            menu.set(14, customMenuButton(
                ExtendedItemIds.SENTRY_WARDEN,
                "<gold>Sentries",
                "<gray>Convert companion eggs into Sentry Posts",
                click -> click.menu().open(guardCraftingMenus.sentriesMenu())
            ));
            menu.set(16, menuButton(
                Material.ECHO_SHARD,
                "<light_purple>Offerings",
                "<gray>Make the twelve sacred offerings",
                click -> click.menu().open(new OfferingsMenu())
            ));
            menu.set(22, menuButton(
                Material.NETHER_STAR,
                "<gold>Divine Favor",
                "<gray>View your path to the Divine Relic",
                click -> click.menu().open(new DivineFavorMenu())
            ));
            menu.set(26, StandardButtons.close(context.theme()));
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
            int[] gridSlots = AltarGuardCraftingMenus.craftingGridSlots();
            for (int i = 0; i < displayed.length; i++) {
                if (displayed[i] != null) {
                    menu.set(gridSlots[i], ExtendedButton.builder(displayed[i]).enabled(false).build());
                }
            }

            boolean ready = hasAllIngredients(player, recipe);
            menu.set(24, ExtendedButton.builder(() -> recipeStatusIcon(player, recipe, ready)).enabled(false).build());
            ExtendedButton.Builder resultButton = ExtendedButton.builder(() -> craftResultIcon(recipe, ready));
            if (ready) {
                resultButton.onClick(click -> craftAtAltar(click.player(), recipe, click.menu()));
            } else {
                resultButton.enabled(false);
            }
            menu.set(25, resultButton.build());
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
            Player player = context.player();
            int completed = completedOfferings(player);
            for (int i = 0; i < OfferingCatalog.all().size(); i++) {
                var offering = OfferingCatalog.all().get(i);
                int slot = RECIPE_SLOTS[i];
                boolean current = i == completed;
                ExtendedButton.Builder button = ExtendedButton.builder(
                    () -> offeringIcon(offering, completed)
                );
                if (current) {
                    button.onClick(click -> makeOffering(click.player(), offering, click.menu()));
                } else {
                    button.enabled(false);
                }
                menu.set(slot, button.build());
            }

            String progress = completed >= 12
                ? "<green>All twelve offerings are complete."
                : "<gray>Completed: <light_purple>" + completed + "<gray> / 12";
            menu.set(31, ExtendedButton.builder(() -> ExtendedItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name("<gold>Divine Favor")
                .lore(
                    progress,
                    completed >= 12
                        ? "<gold>The Divine Relic has been bestowed."
                        : "<gray>Only the highlighted offering may be sacrificed."
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
            int completed = completedOfferings(context.player());
            menu.set(13, ExtendedButton.builder(() -> ExtendedItemBuilder.of(Material.NETHER_STAR)
                .name("<gold>Divine Relic")
                .lore(
                    "<gray>Sacred offerings: <light_purple>" + completed + "<gray> / 12",
                    completed >= 12
                        ? "<green>The ritual is complete."
                        : "<gray>Complete every offering to earn the final artifact."
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

    private ExtendedButton customMenuButton(
        ExtendedItemId itemId,
        String name,
        String lore,
        java.util.function.Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> click
    ) {
        return ExtendedButton.builder(() -> {
                ItemStack item = customItem(itemId);
                item.editMeta(meta -> {
                    meta.displayName(Component.text(
                        name.replaceAll("<[^>]+>", ""),
                        name.contains("gold") ? NamedTextColor.GOLD : NamedTextColor.AQUA
                    ));
                    List<Component> lines = new ArrayList<>();
                    if (meta.lore() != null) {
                        lines.addAll(meta.lore());
                    }
                    lines.add(Component.text(lore.replaceAll("<[^>]+>", ""), NamedTextColor.GRAY));
                    meta.lore(lines);
                });
                return item;
            })
            .onClick(click)
            .build();
    }

    private ItemStack recipeResultIcon(SanctuaryRecipeCatalog.RecipeDefinition recipe) {
        ItemStack item = createCraftResult(recipe);
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

    private ItemStack craftResultIcon(SanctuaryRecipeCatalog.RecipeDefinition recipe, boolean ready) {
        ItemStack item = createCraftResult(recipe);
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.empty());
            lore.add(Component.text(
                ready ? "Click to craft" : "Gather the missing ingredients to craft",
                ready ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
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
            ? customItem(ingredient.extendedItem())
            : new ItemStack(ingredient.material());
    }

    private ItemStack recipeStatusIcon(
        Player player,
        SanctuaryRecipeCatalog.RecipeDefinition recipe,
        boolean ready
    ) {
        Map<String, Integer> needed = new LinkedHashMap<>();
        Map<String, SanctuaryRecipeCatalog.Ingredient> definitions = new LinkedHashMap<>();
        for (var ingredient : flattenedIngredients(recipe)) {
            String key = ingredientKey(ingredient);
            needed.merge(key, 1, Integer::sum);
            definitions.putIfAbsent(key, ingredient);
        }

        List<Component> lore = new ArrayList<>();
        for (var entry : needed.entrySet()) {
            int have = countIngredient(player, definitions.get(entry.getKey()));
            int need = entry.getValue();
            lore.add(Component.text(
                displayName(definitions.get(entry.getKey())) + ": " + have + " / " + need,
                have >= need ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
        }
        lore.add(Component.empty());
        lore.add(Component.text(
            ready ? "You have every exact ingredient." : "Missing exact Sanctuary ingredients.",
            ready ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));

        ItemStack status = new ItemStack(ready ? Material.LIME_DYE : Material.GRAY_DYE);
        status.editMeta(meta -> {
            meta.displayName(Component.text(
                ready ? "READY" : "NOT READY",
                ready ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
            meta.lore(lore);
        });
        return status;
    }

    private ItemStack offeringIcon(OfferingCatalog.Offering offering, int completed) {
        ItemStack item = customItem(offering.itemId());
        item.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.text(
                "Offering " + offering.number() + " of 12",
                NamedTextColor.LIGHT_PURPLE
            ));
            lore.add(Component.text(
                "Reward: " + offering.experiencePoints() + " XP",
                NamedTextColor.GOLD
            ));
            if (offering.number() <= completed) {
                lore.add(Component.text("COMPLETED", NamedTextColor.GREEN));
            } else if (offering.number() == completed + 1) {
                lore.add(Component.text("Click to offer", NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("LOCKED", NamedTextColor.DARK_GRAY));
            }
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack customItem(ExtendedItemId id) {
        ItemStack item = ExtendedItems.create(id);
        if (id.equals(ExtendedItemIds.SEAL_OF_KEEPING) && item.getType() == Material.ENDER_CHEST) {
            item.setType(Material.SHULKER_SHELL);
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private ItemStack createCraftResult(SanctuaryRecipeCatalog.RecipeDefinition recipe) {
        if (recipe.result().equals(ExtendedItemIds.SANCTUARY_BEACON)) {
            return anchorItemService.createUnboundBeacon();
        }
        return customItem(recipe.result());
    }

    private void craftAtAltar(
        Player player,
        SanctuaryRecipeCatalog.RecipeDefinition recipe,
        ExtendedMenuContext menu
    ) {
        if (!hasAllIngredients(player, recipe)) {
            player.sendMessage(Component.text(
                "You no longer have every exact ingredient for that recipe.",
                NamedTextColor.YELLOW
            ));
            menu.refresh();
            return;
        }

        List<ItemStack> consumed = new ArrayList<>();
        for (var ingredient : flattenedIngredients(recipe)) {
            ItemStack taken = takeIngredient(player.getInventory(), ingredient);
            if (taken == null) {
                rollbackIngredients(player, consumed);
                player.sendMessage(Component.text(
                    "The altar could not consume the exact recipe ingredients.",
                    NamedTextColor.YELLOW
                ));
                menu.refresh();
                return;
            }
            consumed.add(taken);
        }

        ItemStack result = createCraftResult(recipe);
        giveOrDrop(player, result);
        advancementService.recordSanctuaryCraft(player, recipe.result());
        player.updateInventory();
        menu.refresh();
    }

    private void makeOffering(
        Player player,
        OfferingCatalog.Offering offering,
        ExtendedMenuContext menu
    ) {
        int completed = completedOfferings(player);
        if (offering.number() != completed + 1) {
            menu.refresh();
            return;
        }

        ItemStack sacrificed = takeExtendedItem(player.getInventory(), offering.itemId());
        if (sacrificed == null) {
            player.sendMessage(Component.text(
                "You need the exact " + pretty(offering.itemId().persistentId()) + " for this offering.",
                NamedTextColor.YELLOW
            ));
            return;
        }

        try {
            if (!offeringProgress.advance(player.getUniqueId(), completed)) {
                giveOrDrop(player, sacrificed);
                player.sendMessage(Component.text(
                    "Your offering progress changed before the sacrifice completed. Try again.",
                    NamedTextColor.YELLOW
                ));
                menu.refresh();
                return;
            }
        } catch (SQLException exception) {
            giveOrDrop(player, sacrificed);
            plugin.getLogger().log(Level.SEVERE, "Failed to save Divine Altar offering progress", exception);
            player.sendMessage(Component.text(
                "The altar could not preserve your offering. Nothing was consumed.",
                NamedTextColor.RED
            ));
            return;
        }

        int newCompleted = completed + 1;
        player.giveExp(offering.experiencePoints());
        advancementService.recordOfferingProgress(player, newCompleted);
        player.sendMessage(Component.text(
            "Offering accepted. +" + offering.experiencePoints() + " XP",
            NamedTextColor.GOLD
        ));

        if (newCompleted == 12) {
            awardPendingDivineRelic(player);
        }
        player.updateInventory();
        menu.refresh();
    }

    private int completedOfferings(Player player) {
        try {
            return offeringProgress.completedOfferings(player.getUniqueId());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read Divine Altar offering progress", exception);
            player.sendMessage(Component.text(
                "The altar could not read your offering progress.",
                NamedTextColor.RED
            ));
            return 0;
        }
    }

    private void awardPendingDivineRelic(Player player) {
        try {
            if (offeringProgress.completedOfferings(player.getUniqueId()) < 12
                || offeringProgress.divineRelicAwarded(player.getUniqueId())) {
                return;
            }

            giveOrDrop(player, customItem(ExtendedItemIds.DIVINE_RELIC));
            advancementService.recordDivineRelicReceived(player);
            offeringProgress.markDivineRelicAwarded(player.getUniqueId());
            player.sendMessage(Component.text(
                "Divine favor answers your devotion. You have received the Divine Relic.",
                NamedTextColor.GOLD
            ));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to finalize Divine Relic reward", exception);
            player.sendMessage(Component.text(
                "Your ritual is complete, but the altar could not finalize the reward. Reopen it to retry.",
                NamedTextColor.RED
            ));
        }
    }

    private ItemStack takeExtendedItem(PlayerInventory inventory, ExtendedItemId id) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack candidate = storage[slot];
            if (candidate == null || !ExtendedItems.is(candidate, id)) {
                continue;
            }
            ItemStack taken = candidate.clone();
            taken.setAmount(1);
            if (candidate.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                candidate.setAmount(candidate.getAmount() - 1);
                inventory.setItem(slot, candidate);
            }
            return taken;
        }
        return null;
    }

    private ItemStack takeIngredient(
        PlayerInventory inventory,
        SanctuaryRecipeCatalog.Ingredient ingredient
    ) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack candidate = storage[slot];
            if (!matchesIngredient(candidate, ingredient)) {
                continue;
            }

            ItemStack taken = candidate.clone();
            taken.setAmount(1);
            if (candidate.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                candidate.setAmount(candidate.getAmount() - 1);
                inventory.setItem(slot, candidate);
            }
            return taken;
        }
        return null;
    }

    private boolean matchesIngredient(ItemStack item, SanctuaryRecipeCatalog.Ingredient ingredient) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (ingredient.extendedItem() != null) {
            return ExtendedItems.is(item, ingredient.extendedItem());
        }
        return item.getType() == ingredient.material()
            && ExtendedItems.getId(item).isEmpty();
    }

    private void rollbackIngredients(Player player, List<ItemStack> consumed) {
        for (ItemStack item : consumed) {
            giveOrDrop(player, item);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
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
        return ingredient.extendedItem() != null
            ? pretty(ingredient.extendedItem().persistentId())
            : pretty(ingredient.material().name());
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

package dev.liamtolkkinen.sanctuary.ui;

import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedItemBuilder;
import dev.liamtolkkinen.extendedui.ExtendedItemProvider;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.extendedui.StandardButtons;
import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.effect.AnchorEffect;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectService;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.territory.TerritoryAreaCalculator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public final class AnchorUiService {
    private final SanctuaryPlugin plugin;
    private final ExtendedUI ui;
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuaryAnchorRepository anchorRepository;
    private final SanctuaryEffectService effectService;
    private final SanctuaryUiService sanctuaryUiService;

    public AnchorUiService(
        SanctuaryPlugin plugin,
        ExtendedUI ui,
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        SanctuaryEffectService effectService,
        SanctuaryUiService sanctuaryUiService
    ) {
        this.plugin = plugin;
        this.ui = ui;
        this.sanctuaryRepository = sanctuaryRepository;
        this.anchorRepository = anchorRepository;
        this.effectService = effectService;
        this.sanctuaryUiService = sanctuaryUiService;
    }

    public void open(Player player, Sanctuary sanctuary, SanctuaryAnchor anchor, boolean adminMode) {
        if (!adminMode && !sanctuary.ownerId().equals(player.getUniqueId())) {
            return;
        }
        ui.open(player, new AnchorMenu(sanctuary.id(), anchor.id(), adminMode));
    }

    private final class AnchorMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final UUID anchorId;
        private final boolean adminMode;

        private AnchorMenu(UUID sanctuaryId, UUID anchorId, boolean adminMode) {
            super(6, "<aqua>Sanctuary Anchor");
            this.sanctuaryId = sanctuaryId;
            this.anchorId = anchorId;
            this.adminMode = adminMode;
        }

        @Override
        public String title(ExtendedMenuContext context) {
            try {
                SanctuaryAnchor anchor = anchorRepository.findById(anchorId).orElse(null);
                if (anchor == null) return "<red>Anchor unavailable";
                return anchor.type() == SanctuaryType.CONDUIT
                    ? "<aqua>Sanctuary Conduit"
                    : "<gold>Sanctuary Beacon";
            } catch (SQLException exception) {
                return "<red>Anchor unavailable";
            }
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            try {
                Sanctuary sanctuary = sanctuaryRepository.findById(sanctuaryId).orElse(null);
                SanctuaryAnchor anchor = anchorRepository.findById(anchorId).orElse(null);
                if (sanctuary == null || anchor == null || !anchor.sanctuaryId().equals(sanctuary.id())) {
                    menu.set(22, button(Material.BARRIER, "<red>Anchor unavailable", List.of(), null));
                    menu.set(53, StandardButtons.close(context.theme()));
                    return;
                }

                int connections = anchorRepository.findNeighborIds(anchor.id()).size();
                List<SanctuaryAnchor> activeAnchors = anchorRepository.findBySanctuary(sanctuary.id()).stream()
                    .filter(value -> value.state() == SanctuaryState.ACTIVE)
                    .filter(value -> value.position().isPresent())
                    .toList();
                double currentTerritoryArea = TerritoryAreaCalculator.currentUnionArea(activeAnchors);

                menu.set(4, button(
                    anchor.type() == SanctuaryType.CONDUIT ? Material.CONDUIT : Material.BEACON,
                    anchor.type() == SanctuaryType.CONDUIT ? "<aqua>Sanctuary Conduit" : "<gold>Sanctuary Beacon",
                    List.of(
                        "<gray>Sanctuary: <white>" + mini(sanctuary.name()),
                        "<gray>Anchor tier: <white>" + roman(anchor.tier()),
                        "<gray>Current radius: <white>" + formatRadius(anchor.territoryRadius()),
                        "<gray>Generation: <white>" + anchor.generation(),
                        "<gray>Graph connections: <white>" + connections,
                        "<gray>Active anchors: <white>" + activeAnchors.size(),
                        "<gray>Current territory: <white>" + formatArea(currentTerritoryArea)
                    ),
                    null
                ));

                menu.set(8, button(
                    Material.RECOVERY_COMPASS,
                    "<yellow>Shared Sanctuary Management",
                    List.of(
                        "<gray>Trust, lockdown, naming, sentries,",
                        "<gray>and other shared Sanctuary state."
                    ),
                    click -> {
                        if (adminMode) sanctuaryUiService.openAdmin(click.player(), sanctuary);
                        else sanctuaryUiService.openPersonal(click.player(), sanctuary);
                    }
                ));

                List<SanctuaryEffectService.AnchorEffectDefinition> safe = effectService.definitions(
                    anchor.type(), AnchorEffect.Target.SAFE);
                List<SanctuaryEffectService.AnchorEffectDefinition> hostile = effectService.definitions(
                    anchor.type(), AnchorEffect.Target.HOSTILE);

                menu.set(18, button(Material.LIME_DYE, "<green>Safe Effects", List.of(
                    "<gray>Owner and trusted players receive these effects."
                ), null));
                menu.set(36, button(Material.RED_DYE, "<red>Hostile Effects", List.of(
                    "<gray>Blacklisted players and Lockdown outsiders receive these effects."
                ), null));

                for (int index = 0; index < 5; index++) {
                    menu.set(10 + index, effectButton(context.player(), anchor, safe.get(index)));
                    menu.set(28 + index, effectButton(context.player(), anchor, hostile.get(index)));
                }
                menu.set(53, StandardButtons.close(context.theme()));
            } catch (SQLException exception) {
                context.player().sendMessage(ChatColor.RED + "Sanctuary could not load this anchor.");
                plugin.getLogger().log(Level.SEVERE, "Failed to load anchor UI " + anchorId, exception);
            }
        }
    }

    private ExtendedButton effectButton(
        Player player,
        SanctuaryAnchor anchor,
        SanctuaryEffectService.AnchorEffectDefinition definition
    ) {
        AnchorEffect effect = definition.effect();
        boolean unlocked = anchor.tier() >= definition.tier();
        int level = 1;
        if (unlocked) {
            try {
                level = effectService.level(anchor, effect);
            } catch (SQLException exception) {
                player.sendMessage(ChatColor.RED + "Sanctuary could not load an anchor effect level.");
            }
        }

        double maximumRadius = plugin.getMaximumTerritoryRadius();
        double effectRadius = Math.min(
            anchor.territoryRadius(),
            effectService.radiusForTier(maximumRadius, definition.tier())
        );
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Unlock tier: <white>" + roman(definition.tier()));
        lore.add("<gray>Current effect radius: <white>" + formatRadius(effectRadius));
        lore.add("<gray>Maximum level: <white>" + roman(effect.maximumLevel()));
        if (!unlocked) {
            lore.add("<red>Locked until Anchor Tier " + roman(definition.tier()) + ".");
        } else {
            lore.add("<green>Current level: " + roman(level));
            if (effect.maximumLevel() > 1) {
                lore.add("<yellow>Click to select the next level.");
            }
        }

        int currentLevel = level;
        return effectIconButton(
            effect,
            (effect.target() == AnchorEffect.Target.SAFE ? "<green>" : "<red>")
                + effectDisplayName(effect),
            lore,
            unlocked && effect.maximumLevel() > 1
                ? click -> cycle(click.player(), anchor.id(), effect, currentLevel, click.menu())
                : null
        );
    }

    private void cycle(
        Player player,
        UUID anchorId,
        AnchorEffect effect,
        int currentLevel,
        ExtendedMenuContext context
    ) {
        try {
            SanctuaryAnchor anchor = anchorRepository.findById(anchorId).orElse(null);
            if (anchor == null) {
                player.sendMessage(ChatColor.RED + "That anchor no longer exists.");
                ui.close(player);
                return;
            }
            int nextLevel = currentLevel >= effect.maximumLevel() ? 1 : currentLevel + 1;
            effectService.setLevel(anchor, effect, nextLevel);
            player.sendMessage(ChatColor.AQUA + effectDisplayName(effect) + " set to " + roman(nextLevel) + ".");
            context.refresh();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private static ExtendedButton effectIconButton(
        AnchorEffect effect,
        String name,
        List<String> lore,
        Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> onClick
    ) {
        ExtendedItemProvider provider = () -> {
            ExtendedItemBuilder builder = ExtendedItemBuilder.of(effectMaterial(effect)).name(name);
            if (!lore.isEmpty()) {
                builder.lore(lore.toArray(String[]::new));
            }
            ItemStack item = builder.build();
            if (effect == AnchorEffect.ELYTRA_DISABLED) {
                item.editMeta(meta -> {
                    if (meta instanceof Damageable damageable) {
                        damageable.setDamage(Material.ELYTRA.getMaxDurability() - 1);
                    }
                });
            }
            return item;
        };
        ExtendedButton.Builder button = ExtendedButton.builder(provider);
        if (onClick != null) {
            button.onClick(onClick);
        }
        return button.build();
    }

    private static ExtendedButton button(
        Material material,
        String name,
        List<String> lore,
        Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> onClick
    ) {
        ExtendedItemProvider provider = () -> {
            ExtendedItemBuilder builder = ExtendedItemBuilder.of(material).name(name);
            if (!lore.isEmpty()) {
                builder.lore(lore.toArray(String[]::new));
            }
            return builder.build();
        };
        ExtendedButton.Builder button = ExtendedButton.builder(provider);
        if (onClick != null) {
            button.onClick(onClick);
        }
        return button.build();
    }

    private static Material effectMaterial(AnchorEffect effect) {
        return switch (effect) {
            case REGENERATION -> Material.GLISTERING_MELON_SLICE;
            case RESISTANCE -> Material.SHIELD;
            case STRENGTH -> Material.BLAZE_POWDER;
            case HASTE -> Material.GOLDEN_PICKAXE;
            case SPEED -> Material.SUGAR;
            case NIGHT_VISION -> Material.ENDER_EYE;
            case DOLPHINS_GRACE -> Material.DOLPHIN_SPAWN_EGG;
            case ELYTRA_DISABLED -> Material.ELYTRA;
            case MINING_FATIGUE -> Material.IRON_PICKAXE;
            case WEAKNESS -> Material.FERMENTED_SPIDER_EYE;
            case BLINDNESS -> Material.INK_SAC;
            case WITHER -> Material.WITHER_ROSE;
            case SLOWNESS -> Material.SOUL_SAND;
        };
    }

    private static String effectDisplayName(AnchorEffect effect) {
        String[] words = effect.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    private static String formatRadius(double radius) {
        return String.format(java.util.Locale.ROOT, "%.1f blocks", radius);
    }

    private static String formatArea(double area) {
        return String.format(java.util.Locale.ROOT, "%,.0f square blocks", area);
    }

    private static String mini(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }
}

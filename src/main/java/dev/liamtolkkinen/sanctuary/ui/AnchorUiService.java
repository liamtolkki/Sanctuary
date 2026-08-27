package dev.liamtolkkinen.sanctuary.ui;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedItemBuilder;
import dev.liamtolkkinen.extendedui.ExtendedItemProvider;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.extendedui.StandardButtons;
import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorTierProgression;
import dev.liamtolkkinen.sanctuary.anchor.AnchorUpgradeService;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.effect.AnchorEffect;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectService;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryAreaCalculator;
import dev.liamtolkkinen.sanctuary.upgrade.AnchorUpgradeType;
import dev.liamtolkkinen.sanctuary.upgrade.SanctuaryUpgradeType;
import dev.liamtolkkinen.sanctuary.upgrade.UpgradeRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

public final class AnchorUiService {
    private static final int LOCKDOWN_UNLOCK_TIER = 3;

    private final SanctuaryPlugin plugin;
    private final ExtendedUI ui;
    private final SanctuaryRepository sanctuaryRepository;
    private final SanctuaryAnchorRepository anchorRepository;
    private final SanctuaryEffectService effectService;
    private final SanctuarySecurityService securityService;
    private final UpgradeRepository upgradeRepository;
    private final SanctuaryUiService sanctuaryUiService;
    private final AnchorUpgradeService upgradeService;

    public AnchorUiService(
        SanctuaryPlugin plugin,
        ExtendedUI ui,
        SanctuaryRepository sanctuaryRepository,
        SanctuaryAnchorRepository anchorRepository,
        SanctuaryEffectService effectService,
        SanctuarySecurityService securityService,
        UpgradeRepository upgradeRepository,
        SanctuaryUiService sanctuaryUiService
    ) {
        this.plugin = plugin;
        this.ui = ui;
        this.sanctuaryRepository = sanctuaryRepository;
        this.anchorRepository = anchorRepository;
        this.effectService = effectService;
        this.securityService = securityService;
        this.upgradeRepository = upgradeRepository;
        this.sanctuaryUiService = sanctuaryUiService;
        this.upgradeService = new AnchorUpgradeService(
            plugin,
            sanctuaryRepository,
            anchorRepository,
            new AnchorItemService(plugin)
        );
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

                menu.set(47, button(
                    Material.ECHO_SHARD,
                    "<light_purple>Relics & Permanent Upgrades",
                    List.of(
                        "<gray>Install anchor-local and Sanctuary-wide relics.",
                        "<gray>Lockdown also becomes available through tier progression."
                    ),
                    click -> click.menu().open(new UpgradeMenu(sanctuary.id(), anchor.id(), adminMode))
                ));
                menu.set(49, upgradeButton(anchor, adminMode));

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

    private final class UpgradeMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final UUID anchorId;
        private final boolean adminMode;

        private UpgradeMenu(UUID sanctuaryId, UUID anchorId, boolean adminMode) {
            super(4, "<light_purple>Relics & Upgrades");
            this.sanctuaryId = sanctuaryId;
            this.anchorId = anchorId;
            this.adminMode = adminMode;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            try {
                Sanctuary sanctuary = sanctuaryRepository.findById(sanctuaryId).orElse(null);
                SanctuaryAnchor anchor = anchorRepository.findById(anchorId).orElse(null);
                if (sanctuary == null || anchor == null || !anchor.sanctuaryId().equals(sanctuary.id())) {
                    menu.set(13, button(Material.BARRIER, "<red>Anchor unavailable", List.of(), null));
                    menu.set(35, StandardButtons.close(context.theme()));
                    return;
                }

                boolean watchersEyeInstalled = upgradeRepository.hasAnchorUpgrade(
                    anchor.id(), AnchorUpgradeType.WATCHERS_EYE
                );
                menu.set(11, button(
                    Material.ENDER_EYE,
                    watchersEyeInstalled ? "<green>Watcher's Eye Installed" : "<aqua>Install Watcher's Eye",
                    watchersEyeInstalled
                        ? List.of(
                            "<green>Permanent anchor-local upgrade.",
                            "<gray>This upgrade stays with the physical anchor."
                        )
                        : List.of(
                            "<gray>Improves this anchor's sentry awareness.",
                            "<gray>Permanent and anchor-local.",
                            "<yellow>Consumes one Watcher's Eye."
                        ),
                    watchersEyeInstalled ? null : click -> installAnchorRelic(
                        click.player(), anchor.id(), ExtendedItemIds.WATCHERS_EYE,
                        AnchorUpgradeType.WATCHERS_EYE, click.menu()
                    )
                ));

                boolean territoryKeystoneInstalled = upgradeRepository.hasSanctuaryUpgrade(
                    sanctuary.id(), SanctuaryUpgradeType.TERRITORY_KEYSTONE
                );
                menu.set(13, button(
                    Material.LODESTONE,
                    territoryKeystoneInstalled
                        ? "<green>Territory Keystone Installed"
                        : "<gold>Install Territory Keystone",
                    territoryKeystoneInstalled
                        ? List.of(
                            "<green>Permanent Sanctuary-wide upgrade.",
                            "<gray>Survives Sanctuary inactivity."
                        )
                        : List.of(
                            "<gray>Unlocks Sanctuary anchor extension progression.",
                            "<gray>Permanent and Sanctuary-wide.",
                            "<yellow>Consumes one Territory Keystone."
                        ),
                    territoryKeystoneInstalled ? null : click -> installSanctuaryRelic(
                        click.player(), sanctuary.id(), ExtendedItemIds.TERRITORY_KEYSTONE,
                        SanctuaryUpgradeType.TERRITORY_KEYSTONE, click.menu()
                    )
                ));

                SanctuarySecurityMode mode = securityService.mode(sanctuary);
                boolean lockdownUnlocked = sanctuary.tier() >= LOCKDOWN_UNLOCK_TIER || adminMode;
                menu.set(15, button(
                    lockdownUnlocked
                        ? (mode == SanctuarySecurityMode.LOCKDOWN ? Material.REDSTONE_TORCH : Material.LEVER)
                        : Material.IRON_BARS,
                    lockdownUnlocked
                        ? "<red>Lockdown: " + (mode == SanctuarySecurityMode.LOCKDOWN ? "Enabled" : "Available")
                        : "<dark_gray>Lockdown Locked",
                    lockdownUnlocked
                        ? List.of(
                            "<gray>Unlocked by reaching Sanctuary Tier " + roman(LOCKDOWN_UNLOCK_TIER) + ".",
                            mode == SanctuarySecurityMode.LOCKDOWN
                                ? "<red>Neutral outsiders are currently hostile."
                                : "<gray>Click to enable Lockdown.",
                            mode == SanctuarySecurityMode.LOCKDOWN
                                ? "<yellow>Click to return to Normal security."
                                : "<yellow>No relic is consumed."
                        )
                        : List.of(
                            "<gray>Reach Sanctuary Tier " + roman(LOCKDOWN_UNLOCK_TIER)
                                + " to unlock Lockdown."
                        ),
                    lockdownUnlocked ? click -> toggleLockdown(click.player(), sanctuary.id(), click.menu()) : null
                ));
            } catch (SQLException exception) {
                context.player().sendMessage(ChatColor.RED + "Sanctuary could not load permanent upgrades.");
                plugin.getLogger().log(Level.SEVERE, "Failed to load Sanctuary upgrades", exception);
            }
            menu.set(27, StandardButtons.back(context.theme()));
            menu.set(35, StandardButtons.close(context.theme()));
        }
    }

    private ExtendedButton upgradeButton(SanctuaryAnchor anchor, boolean adminMode) {
        if (anchor.tier() >= AnchorTierProgression.MAX_TIER) {
            return button(
                Material.NETHER_STAR,
                "<gold>Tier V - Maximum",
                List.of(
                    "<gray>This anchor has reached its maximum tier.",
                    "<gray>Territory radius: <white>" + formatRadius(anchor.territoryRadius())
                ),
                null
            );
        }

        ExtendedItemId requiredItem = AnchorTierProgression.requiredUpgradeItem(anchor.tier());
        int nextTier = AnchorTierProgression.nextTier(anchor.tier());
        double nextRadius = AnchorTierProgression.radiusForTier(
            plugin.getMaximumTerritoryRadius(),
            nextTier
        );

        ExtendedItemProvider provider = () -> {
            ItemStack item = ExtendedItems.create(requiredItem);
            item.editMeta(meta -> {
                List<Component> lore = new ArrayList<>();
                if (meta.lore() != null) lore.addAll(meta.lore());
                lore.add(Component.empty());
                lore.add(Component.text(
                    "Upgrade Tier " + roman(anchor.tier()) + " -> " + roman(nextTier), NamedTextColor.GOLD
                ));
                lore.add(Component.text(
                    "Radius: " + formatRadius(anchor.territoryRadius()) + " -> " + formatRadius(nextRadius),
                    NamedTextColor.GRAY
                ));
                if (nextTier == LOCKDOWN_UNLOCK_TIER) {
                    lore.add(Component.text("Unlocks Sanctuary Lockdown controls.", NamedTextColor.RED));
                }
                lore.add(Component.text(
                    "Consumes one " + pretty(requiredItem.persistentId()) + ".", NamedTextColor.YELLOW
                ));
                lore.add(Component.text("Click to upgrade this anchor.", NamedTextColor.AQUA));
                meta.lore(lore);
            });
            return item;
        };

        return ExtendedButton.builder(provider)
            .onClick(click -> upgradeAnchor(click.player(), anchor.id(), adminMode, click.menu()))
            .build();
    }

    private void upgradeAnchor(Player player, UUID anchorId, boolean adminMode, ExtendedMenuContext context) {
        try {
            SanctuaryAnchor upgraded = upgradeService.upgrade(
                player, anchorId, plugin.getMaximumTerritoryRadius(), adminMode
            );
            player.updateInventory();
            player.sendMessage(
                ChatColor.GOLD + "Sanctuary anchor upgraded to Tier " + roman(upgraded.tier())
                    + ChatColor.GRAY + " with a " + formatRadius(upgraded.territoryRadius()) + " radius."
            );
            if (upgraded.tier() == LOCKDOWN_UNLOCK_TIER) {
                player.sendMessage(ChatColor.RED + "Lockdown controls are now available in Relics & Permanent Upgrades.");
            }
            context.refresh();
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not save this anchor upgrade.");
            plugin.getLogger().log(Level.SEVERE, "Failed to upgrade Sanctuary anchor " + anchorId, exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            context.refresh();
        }
    }

    private ExtendedButton effectButton(
        Player player,
        SanctuaryAnchor anchor,
        SanctuaryEffectService.AnchorEffectDefinition definition
    ) {
        AnchorEffect effect = definition.effect();
        boolean unlocked = anchor.tier() >= definition.tier();
        int effectLevel = 1;
        int attunementLevel = 1;
        int maximumAttunement = effect.maximumLevel();
        AnchorEffect pairedEffect = null;
        int pairedLevel = 1;
        if (unlocked) {
            try {
                pairedEffect = effectService.pairedEffect(anchor.type(), effect);
                maximumAttunement = effectService.maximumAttunementLevel(anchor.type(), effect);
                attunementLevel = effectService.attunementLevel(anchor, effect);
                effectLevel = effectService.level(anchor, effect);
                pairedLevel = effectService.level(anchor, pairedEffect);
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
            lore.add("<green>Current level: " + roman(effectLevel));
            if (pairedEffect != null) {
                lore.add(
                    "<gray>Bound with: <white>" + effectDisplayName(pairedEffect)
                        + " <gray>(Level " + roman(pairedLevel) + ")"
                );
            }
            if (attunementLevel < maximumAttunement) {
                int nextAttunement = attunementLevel + 1;
                int nextEffectLevel = Math.min(nextAttunement, effect.maximumLevel());
                int nextPairedLevel = pairedEffect == null
                    ? nextAttunement
                    : Math.min(nextAttunement, pairedEffect.maximumLevel());
                lore.add("<yellow>Click to advance paired attunement to " + roman(nextAttunement) + ".");
                if (nextEffectLevel == effectLevel && pairedEffect != null) {
                    lore.add(
                        "<gray>" + effectDisplayName(effect) + " stays at " + roman(effectLevel)
                            + "; " + effectDisplayName(pairedEffect) + " advances to " + roman(nextPairedLevel) + "."
                    );
                } else if (pairedEffect != null && nextPairedLevel == pairedLevel) {
                    lore.add(
                        "<gray>" + effectDisplayName(pairedEffect) + " stays at " + roman(pairedLevel)
                            + "; " + effectDisplayName(effect) + " advances to " + roman(nextEffectLevel) + "."
                    );
                } else if (pairedEffect != null) {
                    lore.add("<gray>Both effects advance together.");
                }
                lore.add("<yellow>Consumes one Attunement Relic.");
            } else {
                lore.add("<green>Maximum paired attunement reached.");
            }
        }

        int currentAttunementLevel = attunementLevel;
        int currentMaximumAttunement = maximumAttunement;
        return effectIconButton(
            effect,
            (effect.target() == AnchorEffect.Target.SAFE ? "<green>" : "<red>") + effectDisplayName(effect),
            lore,
            unlocked && currentAttunementLevel < currentMaximumAttunement
                ? click -> upgradeEffect(click.player(), anchor.id(), effect, click.menu())
                : null
        );
    }

    private void upgradeEffect(Player player, UUID anchorId, AnchorEffect effect, ExtendedMenuContext context) {
        try {
            SanctuaryAnchor anchor = anchorRepository.findById(anchorId).orElse(null);
            if (anchor == null) {
                player.sendMessage(ChatColor.RED + "That anchor no longer exists.");
                ui.close(player);
                return;
            }
            if (!effectService.isUnlocked(anchor, effect)) {
                throw new IllegalStateException("That effect is not unlocked at this anchor tier.");
            }

            AnchorEffect pairedEffect = effectService.pairedEffect(anchor.type(), effect);
            int maximumAttunement = effectService.maximumAttunementLevel(anchor.type(), effect);
            int currentAttunement = effectService.attunementLevel(anchor, effect);
            if (currentAttunement >= maximumAttunement) {
                throw new IllegalStateException("That effect pair is already at maximum attunement.");
            }
            if (!hasExactItem(player.getInventory(), ExtendedItemIds.ATTUNEMENT_RELIC)) {
                throw new IllegalStateException("You need an Attunement Relic to upgrade this effect pair.");
            }

            int nextAttunement = currentAttunement + 1;
            effectService.setLevel(anchor, effect, nextAttunement);
            if (!consumeExactItem(player.getInventory(), ExtendedItemIds.ATTUNEMENT_RELIC)) {
                effectService.setLevel(anchor, effect, currentAttunement);
                throw new IllegalStateException(
                    "The Attunement Relic disappeared before the upgrade could finish."
                );
            }

            int effectLevel = effectService.level(anchor, effect);
            int pairedLevel = effectService.level(anchor, pairedEffect);
            player.updateInventory();
            player.sendMessage(
                ChatColor.AQUA + "Paired attunement advanced to " + roman(nextAttunement) + ". "
                    + effectDisplayName(effect) + " is Level " + roman(effectLevel) + ", "
                    + effectDisplayName(pairedEffect) + " is Level " + roman(pairedLevel) + "."
            );
            context.refresh();
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not save this effect upgrade.");
            plugin.getLogger().log(
                Level.SEVERE, "Failed to upgrade Sanctuary anchor effect " + anchorId + ":" + effect.name(), exception
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            context.refresh();
        }
    }

    private void installAnchorRelic(
        Player player,
        UUID anchorId,
        ExtendedItemId itemId,
        AnchorUpgradeType upgrade,
        ExtendedMenuContext context
    ) {
        try {
            if (upgradeRepository.hasAnchorUpgrade(anchorId, upgrade)) {
                throw new IllegalStateException("That anchor already has this permanent upgrade.");
            }
            if (!hasExactItem(player.getInventory(), itemId)) {
                throw new IllegalStateException("You need a " + pretty(itemId.persistentId()) + ".");
            }
            if (!consumeExactItem(player.getInventory(), itemId)) {
                throw new IllegalStateException("The required relic disappeared before installation.");
            }
            try {
                upgradeRepository.installAnchorUpgrade(anchorId, upgrade, Instant.now());
            } catch (SQLException exception) {
                player.getInventory().addItem(ExtendedItems.create(itemId));
                throw exception;
            }
            player.updateInventory();
            player.sendMessage(ChatColor.AQUA + pretty(itemId.persistentId()) + " permanently installed on this anchor.");
            context.refresh();
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not save this anchor upgrade.");
            plugin.getLogger().log(Level.SEVERE, "Failed to install anchor relic " + anchorId, exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            context.refresh();
        }
    }

    private void installSanctuaryRelic(
        Player player,
        UUID sanctuaryId,
        ExtendedItemId itemId,
        SanctuaryUpgradeType upgrade,
        ExtendedMenuContext context
    ) {
        try {
            if (upgradeRepository.hasSanctuaryUpgrade(sanctuaryId, upgrade)) {
                throw new IllegalStateException("This Sanctuary already has that permanent upgrade.");
            }
            if (!hasExactItem(player.getInventory(), itemId)) {
                throw new IllegalStateException("You need a " + pretty(itemId.persistentId()) + ".");
            }
            if (!consumeExactItem(player.getInventory(), itemId)) {
                throw new IllegalStateException("The required relic disappeared before installation.");
            }
            try {
                upgradeRepository.installSanctuaryUpgrade(sanctuaryId, upgrade, Instant.now());
            } catch (SQLException exception) {
                player.getInventory().addItem(ExtendedItems.create(itemId));
                throw exception;
            }
            player.updateInventory();
            player.sendMessage(ChatColor.GOLD + pretty(itemId.persistentId()) + " permanently installed for this Sanctuary.");
            context.refresh();
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not save this Sanctuary upgrade.");
            plugin.getLogger().log(Level.SEVERE, "Failed to install Sanctuary relic " + sanctuaryId, exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            context.refresh();
        }
    }

    private void toggleLockdown(Player player, UUID sanctuaryId, ExtendedMenuContext context) {
        try {
            Sanctuary sanctuary = sanctuaryRepository.findById(sanctuaryId).orElse(null);
            if (sanctuary == null) throw new IllegalStateException("That Sanctuary no longer exists.");
            if (sanctuary.tier() < LOCKDOWN_UNLOCK_TIER && !player.hasPermission("sanctuary.admin")) {
                throw new IllegalStateException(
                    "Lockdown unlocks at Sanctuary Tier " + roman(LOCKDOWN_UNLOCK_TIER) + "."
                );
            }
            SanctuarySecurityMode current = securityService.mode(sanctuary);
            SanctuarySecurityMode next = current == SanctuarySecurityMode.LOCKDOWN
                ? SanctuarySecurityMode.NORMAL
                : SanctuarySecurityMode.LOCKDOWN;
            securityService.setMode(sanctuary, next);
            player.sendMessage(ChatColor.YELLOW + "Sanctuary security mode set to " + next + ".");
            context.refresh();
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not update Lockdown.");
            plugin.getLogger().log(Level.SEVERE, "Failed to update Lockdown " + sanctuaryId, exception);
        } catch (IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            context.refresh();
        }
    }

    private static boolean hasExactItem(PlayerInventory inventory, ExtendedItemId itemId) {
        for (ItemStack item : inventory.getStorageContents()) {
            if (ExtendedItems.is(item, itemId)) return true;
        }
        return false;
    }

    private static boolean consumeExactItem(PlayerInventory inventory, ExtendedItemId itemId) {
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (!ExtendedItems.is(item, itemId)) continue;
            if (item.getAmount() <= 1) inventory.setItem(slot, null);
            else {
                item.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, item);
            }
            return true;
        }
        return false;
    }

    private static ExtendedButton effectIconButton(
        AnchorEffect effect,
        String name,
        List<String> lore,
        Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> onClick
    ) {
        ExtendedItemProvider provider = () -> {
            ExtendedItemBuilder builder = ExtendedItemBuilder.of(effectMaterial(effect)).name(name);
            if (!lore.isEmpty()) builder.lore(lore.toArray(String[]::new));
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
        if (onClick != null) button.onClick(onClick);
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
            if (!lore.isEmpty()) builder.lore(lore.toArray(String[]::new));
            return builder.build();
        };
        ExtendedButton.Builder button = ExtendedButton.builder(provider);
        if (onClick != null) button.onClick(onClick);
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
        return pretty(effect.name());
    }

    private static String pretty(String value) {
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("_");
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

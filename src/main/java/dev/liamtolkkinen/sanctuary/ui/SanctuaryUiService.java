package dev.liamtolkkinen.sanctuary.ui;

import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedItemBuilder;
import dev.liamtolkkinen.extendedui.ExtendedItemProvider;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.ExtendedPagedMenu;
import dev.liamtolkkinen.extendedui.ExtendedTextInputDialog;
import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.extendedui.StandardButtons;
import dev.liamtolkkinen.sanctuary.SanctuaryPlugin;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffect;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectService;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.security.SanctuaryBlacklistEntry;
import dev.liamtolkkinen.sanctuary.security.SanctuaryRelationship;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.security.SanctuaryThreat;
import dev.liamtolkkinen.sanctuary.territory.TerritoryBoundaryService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustEntry;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Sanctuary-owned management menus rendered through ExtendedUI 0.1.0. */
public final class SanctuaryUiService {
    private static final List<Integer> LIST_SLOTS = createListSlots();

    private final SanctuaryPlugin plugin;
    private final ExtendedUI ui;
    private final SanctuaryRepository repository;
    private final SanctuaryPermissionService permissionService;
    private final SanctuarySecurityService securityService;
    private final SanctuaryEffectService effectService;
    private final TerritoryBoundaryService boundaryService;

    public SanctuaryUiService(
        SanctuaryPlugin plugin,
        ExtendedUI ui,
        SanctuaryRepository repository,
        SanctuaryPermissionService permissionService,
        SanctuarySecurityService securityService,
        SanctuaryEffectService effectService,
        TerritoryBoundaryService boundaryService
    ) {
        this.plugin = plugin;
        this.ui = ui;
        this.repository = repository;
        this.permissionService = permissionService;
        this.securityService = securityService;
        this.effectService = effectService;
        this.boundaryService = boundaryService;
    }

    public void openPersonal(Player player, Sanctuary sanctuary) {
        if (!sanctuary.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "That Sanctuary does not belong to you.");
            return;
        }
        ui.open(player, new MainMenu(sanctuary.id(), false));
    }

    public void openAdmin(Player player, Sanctuary sanctuary) {
        if (!player.hasPermission("sanctuary.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open the Sanctuary admin UI.");
            return;
        }
        ui.open(player, new MainMenu(sanctuary.id(), true));
    }

    public boolean openAdminBySelector(Player player, String selector) {
        if (!player.hasPermission("sanctuary.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to open the Sanctuary admin UI.");
            return true;
        }
        try {
            List<Sanctuary> candidates = repository.findAll();
            Optional<Sanctuary> result = resolveSelector(selector, candidates);
            if (result.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No Sanctuary matches '" + selector + "'.");
                return true;
            }
            openAdmin(player, result.orElseThrow());
        } catch (SQLException exception) {
            player.sendMessage(ChatColor.RED + "Sanctuary could not open the admin UI.");
            plugin.getLogger().log(Level.SEVERE, "Failed to open Sanctuary admin UI", exception);
        }
        return true;
    }

    public List<String> adminSelectorLabels() {
        try {
            List<Sanctuary> candidates = repository.findAll();
            return candidates.stream()
                .map(value -> selectorLabel(value, candidates))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to build Sanctuary UI autocomplete", exception);
            return List.of();
        }
    }

    private final class MainMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private MainMenu(UUID sanctuaryId, boolean adminMode) {
            super(5, adminMode ? "<dark_red>Sanctuary Admin" : "<gold>Sanctuary");
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        public String title(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                return "<red>Sanctuary unavailable";
            }
            return (adminMode ? "<dark_red>Admin | " : "<gold>") + mini(sanctuary.name());
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(22, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(
                    "<gray>The database record is missing or access changed."
                ), click -> click.menu().close()));
                return;
            }

            menu.set(4, infoButton(sanctuary, adminMode));

            if (sanctuary.ownerId().equals(context.player().getUniqueId())) {
                menu.set(18, button(
                    Material.NAME_TAG,
                    "<yellow>Rename Sanctuary",
                    List.of(
                        "<gray>Current name: <white>" + mini(sanctuary.name()),
                        "<gray>Click to choose a new name."
                    ),
                    click -> showRenameDialog(click.menu(), sanctuary, adminMode)
                ));
            }

            menu.set(20, button(
                sanctuary.state() == SanctuaryState.ACTIVE ? Material.ENDER_EYE : Material.GRAY_DYE,
                "<aqua>Show Boundary",
                sanctuary.state() == SanctuaryState.ACTIVE
                    ? List.of("<gray>Display this boundary only to you.")
                    : List.of("<dark_gray>Unavailable while " + sanctuary.state() + "."),
                click -> showBoundary(click.player(), sanctuary)
            ));
            menu.set(22, button(
                Material.PLAYER_HEAD,
                "<gold>Players & Access",
                List.of("<gray>Manage trusted and blacklisted players."),
                click -> click.menu().open(new AccessMenu(sanctuary.id(), adminMode))
            ));
            menu.set(24, button(
                Material.SHIELD,
                "<red>Security",
                List.of("<gray>View security mode and threat policy."),
                click -> click.menu().open(new SecurityMenu(sanctuary.id(), adminMode))
            ));

            if (adminMode && sanctuary.debugEphemeral()) {
                menu.set(26, button(
                    Material.COMMAND_BLOCK,
                    "<light_purple>My Debug Permissions",
                    List.of("<gray>Toggle your own permissions for", "<gray>solo protection testing."),
                    click -> click.menu().open(
                        new CapabilityMenu(sanctuary.id(), true, click.player().getUniqueId())
                    )
                ));
            } else if (adminMode) {
                menu.set(26, button(
                    Material.PAPER,
                    "<yellow>Admin View",
                    List.of("<gray>You are inspecting this Sanctuary as an admin."),
                    null
                ));
            }

            menu.set(40, StandardButtons.close(context.theme()));
        }
    }

    private final class AccessMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private AccessMenu(UUID sanctuaryId, boolean adminMode) {
            super(3, "<gold>Players & Access");
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(13, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(), null));
                return;
            }
            menu.set(11, button(
                Material.LIME_DYE,
                "<green>Trusted Players",
                List.of("<gray>Trusted players remain safe even during Lockdown.",
                    "<gray>Hard-protection capabilities are managed here too."),
                click -> click.menu().open(new TrustMenu(sanctuaryId, adminMode))
            ));
            menu.set(15, button(
                Material.RED_DYE,
                "<red>Blacklist",
                List.of("<gray>Blacklisted players are always treated as hostile."),
                click -> click.menu().open(new BlacklistMenu(sanctuaryId, adminMode))
            ));
            menu.set(18, StandardButtons.back(context.theme()));
            menu.set(26, StandardButtons.close(context.theme()));
        }
    }

    private final class TrustMenu extends ExtendedPagedMenu<SanctuaryTrustEntry> {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private TrustMenu(UUID sanctuaryId, boolean adminMode) {
            super(6, "<gold>Trust & Capabilities", LIST_SLOTS, 47, 51);
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        protected List<SanctuaryTrustEntry> items(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                return List.of();
            }
            try {
                return permissionService.trustedPlayers(sanctuary).stream()
                    .sorted(Comparator.comparing(
                        value -> playerLabel(value.playerId()),
                        String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load trusted players", exception);
                return List.of();
            }
        }

        @Override
        protected ExtendedButton buttonFor(
            ExtendedMenuContext context,
            SanctuaryTrustEntry entry,
            int absoluteIndex
        ) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Capabilities:");
            if (entry.capabilities().isEmpty()) {
                lore.add("<red>  none");
            } else {
                entry.capabilities().stream()
                    .sorted(Comparator.comparing(Enum::name))
                    .forEach(value -> lore.add("<green>  " + capabilityDisplayName(value)));
            }
            lore.add("");
            lore.add("<yellow>Click to edit permissions.");
            return button(
                Material.PLAYER_HEAD,
                "<gold>" + mini(playerLabel(entry.playerId())),
                lore,
                click -> click.menu().open(
                    new CapabilityMenu(sanctuaryId, adminMode, entry.playerId())
                )
            );
        }

        @Override
        protected void buildStatic(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(22, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(), null));
                menu.set(53, StandardButtons.close(context.theme()));
                return;
            }

            menu.set(4, button(
                Material.GOLDEN_HELMET,
                "<gold>Owner: " + mini(playerLabel(sanctuary.ownerId())),
                List.of("<green>All capabilities are always allowed."),
                null
            ));
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(49, button(
                Material.EMERALD,
                "<green>Add Online Player",
                List.of("<gray>Choose a player who is currently online."),
                click -> click.menu().open(new AddTrustMenu(sanctuaryId, adminMode))
            ));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private final class AddTrustMenu extends ExtendedPagedMenu<Player> {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private AddTrustMenu(UUID sanctuaryId, boolean adminMode) {
            super(6, "<green>Add Trusted Player", LIST_SLOTS, 47, 51);
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        protected List<Player> items(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                return List.of();
            }
            try {
                Set<UUID> trusted = permissionService.trustedPlayers(sanctuary).stream()
                    .map(SanctuaryTrustEntry::playerId)
                    .collect(java.util.stream.Collectors.toSet());
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player.class::cast)
                    .filter(value -> !value.getUniqueId().equals(sanctuary.ownerId()))
                    .filter(value -> !trusted.contains(value.getUniqueId()))
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load trust candidates", exception);
                return List.of();
            }
        }

        @Override
        protected ExtendedButton buttonFor(ExtendedMenuContext context, Player target, int absoluteIndex) {
            return ExtendedButton.builder(() ->
                    ExtendedItemBuilder.of(Material.PLAYER_HEAD)
                        .playerHead(target)
                        .name("<green>" + mini(target.getName()))
                        .lore("<gray>Click to trust this player.")
                        .build()
                )
                .onClick(click -> {
                    Sanctuary sanctuary = loadForMenu(click.player(), sanctuaryId, adminMode);
                    if (sanctuary == null) {
                        return;
                    }
                    try {
                        securityService.prepareForTrust(sanctuary, target.getUniqueId());
                        permissionService.trust(sanctuary, target.getUniqueId(), Instant.now());
                        click.player().sendMessage(
                            ChatColor.GREEN + "Trusted " + target.getName() + " in " + sanctuary.name() + "."
                        );
                        click.menu().open(
                            new CapabilityMenu(sanctuaryId, adminMode, target.getUniqueId())
                        );
                    } catch (SQLException | IllegalArgumentException exception) {
                        click.player().sendMessage(ChatColor.RED + exception.getMessage());
                    }
                })
                .build();
        }

        @Override
        protected void buildStatic(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private final class BlacklistMenu extends ExtendedPagedMenu<SanctuaryBlacklistEntry> {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private BlacklistMenu(UUID sanctuaryId, boolean adminMode) {
            super(6, "<red>Blacklist", LIST_SLOTS, 47, 51);
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        protected List<SanctuaryBlacklistEntry> items(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                return List.of();
            }
            try {
                return securityService.blacklistedPlayers(sanctuary).stream()
                    .sorted(Comparator.comparing(
                        value -> playerLabel(value.playerId()),
                        String.CASE_INSENSITIVE_ORDER
                    ))
                    .toList();
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load blacklist", exception);
                return List.of();
            }
        }

        @Override
        protected ExtendedButton buttonFor(
            ExtendedMenuContext context,
            SanctuaryBlacklistEntry entry,
            int absoluteIndex
        ) {
            return button(
                Material.PLAYER_HEAD,
                "<red>" + mini(playerLabel(entry.playerId())),
                List.of(
                    "<gray>Always treated as hostile.",
                    "<dark_gray>Blacklisted: " + entry.createdAt(),
                    "",
                    "<yellow>Click to remove from blacklist."
                ),
                click -> {
                    Sanctuary sanctuary = loadForMenu(click.player(), sanctuaryId, adminMode);
                    if (sanctuary == null) {
                        return;
                    }
                    try {
                        securityService.unblacklist(sanctuary, entry.playerId());
                        click.player().sendMessage(ChatColor.YELLOW + "Removed "
                            + playerLabel(entry.playerId()) + " from the blacklist.");
                        click.menu().refresh();
                    } catch (SQLException | IllegalArgumentException exception) {
                        click.player().sendMessage(ChatColor.RED + exception.getMessage());
                    }
                }
            );
        }

        @Override
        protected void buildStatic(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(49, button(
                Material.REDSTONE,
                "<red>Blacklist Online Player",
                List.of("<gray>Choose a player who is currently online."),
                click -> click.menu().open(new AddBlacklistMenu(sanctuaryId, adminMode))
            ));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private final class AddBlacklistMenu extends ExtendedPagedMenu<Player> {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private AddBlacklistMenu(UUID sanctuaryId, boolean adminMode) {
            super(6, "<red>Blacklist Player", LIST_SLOTS, 47, 51);
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        protected List<Player> items(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                return List.of();
            }
            try {
                Set<UUID> blacklisted = securityService.blacklistedPlayers(sanctuary).stream()
                    .map(SanctuaryBlacklistEntry::playerId)
                    .collect(java.util.stream.Collectors.toSet());
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player.class::cast)
                    .filter(value -> !value.getUniqueId().equals(sanctuary.ownerId()))
                    .filter(value -> !blacklisted.contains(value.getUniqueId()))
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load blacklist candidates", exception);
                return List.of();
            }
        }

        @Override
        protected ExtendedButton buttonFor(ExtendedMenuContext context, Player target, int absoluteIndex) {
            return ExtendedButton.builder(() ->
                    ExtendedItemBuilder.of(Material.PLAYER_HEAD)
                        .playerHead(target)
                        .name("<red>" + mini(target.getName()))
                        .lore("<gray>Click to blacklist this player.",
                            "<yellow>If trusted, trust will be removed.")
                        .build()
                )
                .onClick(click -> {
                    Sanctuary sanctuary = loadForMenu(click.player(), sanctuaryId, adminMode);
                    if (sanctuary == null) {
                        return;
                    }
                    try {
                        securityService.blacklist(sanctuary, target.getUniqueId(), Instant.now());
                        click.player().sendMessage(ChatColor.RED + "Blacklisted "
                            + target.getName() + " in " + sanctuary.name() + ".");
                        click.menu().goBack();
                    } catch (SQLException | IllegalArgumentException exception) {
                        click.player().sendMessage(ChatColor.RED + exception.getMessage());
                    }
                })
                .build();
        }

        @Override
        protected void buildStatic(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private final class SecurityMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private SecurityMenu(UUID sanctuaryId, boolean adminMode) {
            super(4, "<red>Sanctuary Security");
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(13, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(), null));
                return;
            }
            try {
                SanctuarySecurityMode mode = securityService.mode(sanctuary);
                SanctuaryRelationship relationship = securityService.relationship(
                    sanctuary, context.player().getUniqueId());
                SanctuaryThreat threat = securityService.threat(sanctuary, context.player().getUniqueId());

                menu.set(4, button(
                    mode == SanctuarySecurityMode.LOCKDOWN ? Material.REDSTONE_TORCH : Material.TORCH,
                    "<gold>Security Mode: " + modeDisplayName(mode),
                    List.of(
                        mode == SanctuarySecurityMode.NORMAL
                            ? "<gray>Only explicitly blacklisted players are hostile."
                            : "<red>All players except owner/trusted are hostile.",
                        "<gray>Your relationship: <white>" + relationship,
                        "<gray>Your effective threat: <white>" + threat
                    ),
                    null
                ));

                boolean canDebugToggle = adminMode && context.player().hasPermission("sanctuary.admin");
                menu.set(13, button(
                    canDebugToggle ? Material.LEVER : Material.IRON_BARS,
                    canDebugToggle ? "<yellow>Admin: Toggle Security Mode" : "<dark_gray>Lockdown Upgrade",
                    canDebugToggle
                        ? List.of("<gray>Admin/debug control until Beacon tier gating is implemented.",
                            "<yellow>Click to switch Normal / Lockdown.")
                        : List.of("<gray>Lockdown will unlock at a higher Beacon tier."),
                    canDebugToggle ? click -> {
                        try {
                            SanctuarySecurityMode next = mode == SanctuarySecurityMode.NORMAL
                                ? SanctuarySecurityMode.LOCKDOWN
                                : SanctuarySecurityMode.NORMAL;
                            securityService.setMode(sanctuary, next);
                            click.player().sendMessage(ChatColor.YELLOW + "Security mode set to " + next + ".");
                            click.menu().refresh();
                        } catch (SQLException exception) {
                            menuError(click.player(), "Failed to update security mode", exception);
                        }
                    } : null
                ));

                menu.set(20, button(
                    plugin.areHardProtectionsEnabled() ? Material.IRON_DOOR : Material.OAK_DOOR,
                    plugin.areHardProtectionsEnabled()
                        ? "<yellow>Hard Protections: Enabled"
                        : "<gray>Hard Protections: Disabled",
                    plugin.areHardProtectionsEnabled()
                        ? List.of(
                            "<gray>Server config may physically block selected actions.",
                            "<dark_gray>Configured under protections.hard."
                        )
                        : List.of(
                            "<gray>Sanctuary does not physically block ordinary actions.",
                            "<gray>Defense will come from Beacon effects and sentries."
                        ),
                    null
                ));

                menu.set(22, button(
                    Material.BEACON,
                    "<aqua>Beacon Effects",
                    List.of(
                        "<gray>View and select each unlocked effect level.",
                        "<gray>Effect radii are derived from the maximum radius."
                    ),
                    click -> click.menu().open(new EffectMenu(sanctuary.id(), adminMode))
                ));

                menu.set(24, button(
                    Material.WHITE_DYE,
                    "<white>Boundary Relationship Colors",
                    List.of(
                        "<blue>Blue <gray>- owner",
                        "<green>Green <gray>- trusted",
                        "<white>White <gray>- neutral",
                        "<red>Red <gray>- hostile / blacklisted / lockdown outsider"
                    ),
                    null
                ));

                if (adminMode && sanctuary.debugEphemeral()) {
                    menu.set(31, button(
                        Material.PLAYER_HEAD,
                        "<light_purple>My Debug Relationship",
                        List.of(
                            "<gray>Switch yourself between Trusted,",
                            "<gray>Neutral, and Blacklisted for effect testing."
                        ),
                        click -> click.menu().open(new DebugRelationshipMenu(sanctuary.id()))
                    ));
                }
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load Sanctuary security", exception);
            }
            menu.set(27, StandardButtons.back(context.theme()));
            menu.set(35, StandardButtons.close(context.theme()));
        }
    }

    private final class EffectMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private EffectMenu(UUID sanctuaryId, boolean adminMode) {
            super(6, "<aqua>Beacon Effects");
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(22, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(), null));
                return;
            }

            double maximumRadius = plugin.getMaximumTerritoryRadius();
            menu.set(4, button(
                Material.BEACON,
                "<gold>Beacon Tier " + roman(sanctuary.tier()),
                List.of(
                    "<gray>Maximum configured radius: <white>" + formatRadius(maximumRadius),
                    "<gray>Segment delta: <white>" + formatRadius(effectService.segmentDelta(maximumRadius)),
                    "<gray>Effects stack inward toward the Beacon."
                ),
                null
            ));

            SanctuaryEffect[] positive = {
                SanctuaryEffect.REGENERATION,
                SanctuaryEffect.RESISTANCE,
                SanctuaryEffect.STRENGTH,
                SanctuaryEffect.HASTE,
                SanctuaryEffect.SPEED
            };
            SanctuaryEffect[] hostile = {
                SanctuaryEffect.ELYTRA_DISABLED,
                SanctuaryEffect.MINING_FATIGUE,
                SanctuaryEffect.WEAKNESS,
                SanctuaryEffect.BLINDNESS,
                SanctuaryEffect.WITHER
            };

            for (int index = 0; index < positive.length; index++) {
                menu.set(10 + index, effectButton(context.player(), sanctuary, positive[index], maximumRadius));
                menu.set(28 + index, effectButton(context.player(), sanctuary, hostile[index], maximumRadius));
            }

            menu.set(18, button(Material.LIME_DYE, "<green>Safe Effects", List.of(
                "<gray>Owner and trusted players receive these effects."
            ), null));
            menu.set(36, button(Material.RED_DYE, "<red>Hostile Effects", List.of(
                "<gray>Blacklisted players and Lockdown outsiders receive these effects."
            ), null));
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private final class DebugRelationshipMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;

        private DebugRelationshipMenu(UUID sanctuaryId) {
            super(3, "<light_purple>Debug Relationship");
            this.sanctuaryId = sanctuaryId;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, true);
            if (sanctuary == null || !sanctuary.debugEphemeral()) {
                menu.set(13, button(Material.BARRIER, "<red>Debug Sanctuary unavailable", List.of(), null));
                return;
            }
            try {
                SanctuaryRelationship current = securityService.relationship(
                    sanctuary,
                    context.player().getUniqueId()
                );
                menu.set(4, button(
                    Material.PLAYER_HEAD,
                    "<gold>Current: " + current,
                    List.of("<gray>Effective threat: <white>" + securityService.threat(
                        sanctuary,
                        context.player().getUniqueId()
                    )),
                    null
                ));
                menu.set(11, debugRelationshipButton(
                    sanctuary,
                    SanctuaryRelationship.TRUSTED,
                    current,
                    Material.LIME_DYE,
                    "<green>Trusted"
                ));
                menu.set(13, debugRelationshipButton(
                    sanctuary,
                    SanctuaryRelationship.NEUTRAL,
                    current,
                    Material.WHITE_DYE,
                    "<white>Neutral / Unconfigured"
                ));
                menu.set(15, debugRelationshipButton(
                    sanctuary,
                    SanctuaryRelationship.BLACKLISTED,
                    current,
                    Material.RED_DYE,
                    "<red>Blacklisted"
                ));
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load debug relationship", exception);
            }
            menu.set(18, StandardButtons.back(context.theme()));
            menu.set(26, StandardButtons.close(context.theme()));
        }
    }

    private final class CapabilityMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;
        private final UUID targetPlayerId;

        private CapabilityMenu(UUID sanctuaryId, boolean adminMode, UUID targetPlayerId) {
            super(4, "<gold>Player Capabilities");
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
            this.targetPlayerId = targetPlayerId;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(13, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(), null));
                menu.set(35, StandardButtons.close(context.theme()));
                return;
            }

            boolean owner = sanctuary.ownerId().equals(targetPlayerId);
            boolean trusted;
            Set<SanctuaryCapability> effective;
            try {
                trusted = permissionService.isTrusted(sanctuary, targetPlayerId);
                effective = permissionService.effectiveCapabilities(sanctuary, targetPlayerId);
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load capabilities", exception);
                return;
            }

            menu.set(4, button(
                Material.PLAYER_HEAD,
                "<gold>" + mini(playerLabel(targetPlayerId)),
                List.of(
                    "<gray>Owner: " + (owner ? "<green>yes" : "<red>no"),
                    "<gray>Trusted: " + (trusted ? "<green>yes" : "<red>no")
                ),
                null
            ));

            SanctuaryCapability[] capabilities = SanctuaryCapability.values();
            for (int index = 0; index < capabilities.length; index++) {
                SanctuaryCapability capability = capabilities[index];
                boolean allowed = effective.contains(capability);
                List<String> lore = new ArrayList<>();
                lore.add(allowed ? "<green>ALLOWED" : "<red>DENIED");
                if (owner) {
                    lore.add("<dark_gray>The owner always has this capability.");
                } else {
                    lore.add("<gray>Click to " + (allowed ? "deny" : "allow") + ".");
                }
                menu.set(
                    10 + index,
                    button(
                        allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                        (allowed ? "<green>" : "<red>") + capabilityDisplayName(capability),
                        lore,
                        owner ? null : click -> toggleCapability(
                            click.player(), sanctuary, targetPlayerId, capability, click.menu()
                        )
                    )
                );
            }

            if (!owner) {
                menu.set(22, button(
                    Material.RED_DYE,
                    "<red>Remove Trust",
                    List.of("<gray>Remove this player and all capability grants."),
                    click -> {
                        try {
                            permissionService.untrust(sanctuary, targetPlayerId);
                            click.player().sendMessage(
                                ChatColor.YELLOW + "Removed trust for " + playerLabel(targetPlayerId) + "."
                            );
                            click.menu().goBack();
                        } catch (SQLException | IllegalArgumentException exception) {
                            click.player().sendMessage(ChatColor.RED + exception.getMessage());
                        }
                    }
                ));
            }

            if (adminMode && sanctuary.debugEphemeral() && targetPlayerId.equals(context.player().getUniqueId())) {
                menu.set(30, button(
                    Material.LIME_CONCRETE,
                    "<green>Allow All Debug Capabilities",
                    List.of("<gray>Useful for solo protection testing."),
                    click -> allowAllDebug(click.player(), sanctuary, click.menu())
                ));
                menu.set(32, button(
                    Material.RED_CONCRETE,
                    "<red>Clear My Debug Trust",
                    List.of("<gray>Return yourself to outsider behavior."),
                    click -> clearDebugTrust(click.player(), sanctuary, click.menu())
                ));
            }

            menu.set(27, StandardButtons.back(context.theme()));
            menu.set(35, StandardButtons.close(context.theme()));
        }
    }


    private ExtendedButton effectButton(
        Player player,
        Sanctuary sanctuary,
        SanctuaryEffect effect,
        double maximumRadius
    ) {
        boolean unlocked = effectService.isUnlocked(sanctuary, effect);
        int level = 1;
        if (unlocked) {
            try {
                level = effectService.level(sanctuary, effect);
            } catch (SQLException exception) {
                menuError(player, "Failed to load Beacon effect level", exception);
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Effect tier: <white>" + roman(effect.tier()));
        lore.add("<gray>Radius: <white>" + formatRadius(effectService.radiusForTier(maximumRadius, effect.tier())));
        lore.add("<gray>Maximum level: <white>" + roman(effect.maximumLevel()));
        if (!unlocked) {
            lore.add("<red>Locked until Beacon Tier " + roman(effect.tier()) + ".");
        } else {
            lore.add("<green>Current level: " + roman(level));
            if (effect.maximumLevel() > 1) {
                lore.add("<yellow>Click to select the next level.");
                lore.add("<dark_gray>Debug/free selection for now. No item is consumed.");
            } else {
                lore.add("<dark_gray>This effect has no amplifier upgrades.");
            }
        }

        int currentLevel = level;
        return button(
            effectMaterial(effect),
            (effect.target() == SanctuaryEffect.EffectTarget.SAFE ? "<green>" : "<red>")
                + effectDisplayName(effect),
            lore,
            unlocked && effect.maximumLevel() > 1
                ? click -> cycleEffectLevel(click.player(), sanctuary, effect, currentLevel, click.menu())
                : null
        );
    }

    private ExtendedButton debugRelationshipButton(
        Sanctuary sanctuary,
        SanctuaryRelationship relationship,
        SanctuaryRelationship current,
        Material material,
        String name
    ) {
        return button(
            material,
            name + (current == relationship ? " <yellow>[SELECTED]" : ""),
            List.of("<gray>Click to make yourself " + relationship.name().toLowerCase(Locale.ROOT) + "."),
            click -> setDebugRelationship(click.player(), sanctuary, relationship, click.menu())
        );
    }

    private void cycleEffectLevel(
        Player player,
        Sanctuary sanctuary,
        SanctuaryEffect effect,
        int currentLevel,
        ExtendedMenuContext context
    ) {
        int nextLevel = currentLevel >= effect.maximumLevel() ? 1 : currentLevel + 1;
        try {
            effectService.setLevel(sanctuary, effect, nextLevel);
            player.sendMessage(ChatColor.AQUA + effectDisplayName(effect) + " set to " + roman(nextLevel) + ".");
            context.refresh();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void setDebugRelationship(
        Player player,
        Sanctuary sanctuary,
        SanctuaryRelationship relationship,
        ExtendedMenuContext context
    ) {
        try {
            UUID playerId = player.getUniqueId();
            if (permissionService.isTrusted(sanctuary, playerId)) {
                permissionService.untrust(sanctuary, playerId);
            }
            if (securityService.isBlacklisted(sanctuary, playerId)) {
                securityService.unblacklist(sanctuary, playerId);
            }

            switch (relationship) {
                case TRUSTED -> {
                    securityService.prepareForTrust(sanctuary, playerId);
                    permissionService.trust(sanctuary, playerId, Instant.now());
                }
                case BLACKLISTED -> securityService.blacklist(sanctuary, playerId, Instant.now());
                case NEUTRAL -> {
                    // Removing both explicit states is the neutral/unconfigured state.
                }
                case OWNER -> throw new IllegalArgumentException("Debug users cannot become the synthetic owner.");
            }
            player.sendMessage(ChatColor.YELLOW + "Debug relationship set to " + relationship + ".");
            context.refresh();
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void showRenameDialog(
        ExtendedMenuContext context,
        Sanctuary sanctuary,
        boolean adminMode
    ) {
        if (!sanctuary.ownerId().equals(context.player().getUniqueId())) {
            context.player().sendMessage(ChatColor.RED + "Only the Sanctuary owner can rename it.");
            return;
        }

        ExtendedTextInputDialog dialog = ExtendedTextInputDialog.builder(
                Component.text("Rename Sanctuary"),
                Component.text("Sanctuary name")
            )
            .initialValue(sanctuary.name())
            .maxLength(32)
            .confirmText(Component.text("Rename"))
            .cancelText(Component.text("Cancel"))
            .onConfirm((player, value) -> renameSanctuary(player, sanctuary.id(), value, adminMode))
            .build();

        context.showDialog(dialog);
    }

    private void renameSanctuary(
        Player player,
        UUID sanctuaryId,
        String requestedName,
        boolean adminMode
    ) {
        final String name;
        try {
            name = normalizeSanctuaryName(requestedName);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            refreshOrReopen(player, sanctuaryId, adminMode);
            return;
        }

        try {
            Sanctuary current = repository.findById(sanctuaryId).orElse(null);
            if (current == null) {
                player.sendMessage(ChatColor.RED + "That Sanctuary no longer exists.");
                ui.close(player);
                return;
            }
            if (!current.ownerId().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Only the Sanctuary owner can rename it.");
                refreshOrReopen(player, sanctuaryId, adminMode);
                return;
            }

            Sanctuary renamed = new Sanctuary(
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
            );
            repository.save(renamed);
            player.sendMessage(ChatColor.GREEN + "Sanctuary renamed to " + name + ".");
            refreshOrReopen(player, sanctuaryId, adminMode);
        } catch (SQLException | IllegalArgumentException exception) {
            menuError(player, "Failed to rename Sanctuary", exception);
            refreshOrReopen(player, sanctuaryId, adminMode);
        }
    }

    private void refreshOrReopen(Player player, UUID sanctuaryId, boolean adminMode) {
        if (ui.hasSession(player)) {
            ui.refresh(player);
            return;
        }
        ui.open(player, new MainMenu(sanctuaryId, adminMode));
    }

    private void toggleCapability(
        Player player,
        Sanctuary sanctuary,
        UUID targetPlayerId,
        SanctuaryCapability capability,
        ExtendedMenuContext context
    ) {
        try {
            Set<SanctuaryCapability> effective = permissionService.effectiveCapabilities(sanctuary, targetPlayerId);
            boolean currentlyAllowed = effective.contains(capability);
            if (!currentlyAllowed && !permissionService.isTrusted(sanctuary, targetPlayerId)) {
                securityService.prepareForTrust(sanctuary, targetPlayerId);
                permissionService.trust(sanctuary, targetPlayerId, Instant.now());
            }
            permissionService.setCapability(sanctuary, targetPlayerId, capability, !currentlyAllowed);
            context.refresh();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void allowAllDebug(Player player, Sanctuary sanctuary, ExtendedMenuContext context) {
        try {
            if (!permissionService.isTrusted(sanctuary, player.getUniqueId())) {
                securityService.prepareForTrust(sanctuary, player.getUniqueId());
                permissionService.trust(sanctuary, player.getUniqueId(), Instant.now());
            }
            for (SanctuaryCapability capability : SanctuaryCapability.values()) {
                permissionService.setCapability(sanctuary, player.getUniqueId(), capability, true);
            }
            player.sendMessage(ChatColor.GREEN + "All debug capabilities enabled.");
            context.refresh();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void clearDebugTrust(Player player, Sanctuary sanctuary, ExtendedMenuContext context) {
        try {
            if (permissionService.isTrusted(sanctuary, player.getUniqueId())) {
                permissionService.untrust(sanctuary, player.getUniqueId());
            }
            player.sendMessage(ChatColor.YELLOW + "Debug trust cleared.");
            context.refresh();
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void showBoundary(Player player, Sanctuary sanctuary) {
        if (sanctuary.state() != SanctuaryState.ACTIVE || sanctuary.position().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Only an active Sanctuary has a boundary to display.");
            return;
        }
        boundaryService.show(
            player,
            sanctuary,
            plugin.getBoundaryParticleSpacing(),
            plugin.getBoundaryDisplaySeconds()
        );
        ui.close(player);
        player.sendMessage(
            ChatColor.GREEN + "Displaying " + sanctuary.name() + " boundary for "
                + plugin.getBoundaryDisplaySeconds() + " seconds."
        );
    }

    private Sanctuary loadForMenu(Player player, UUID sanctuaryId, boolean adminMode) {
        try {
            Sanctuary sanctuary = repository.findById(sanctuaryId).orElse(null);
            if (sanctuary == null) {
                return null;
            }
            if (adminMode) {
                return player.hasPermission("sanctuary.admin") ? sanctuary : null;
            }
            return sanctuary.ownerId().equals(player.getUniqueId()) ? sanctuary : null;
        } catch (SQLException exception) {
            menuError(player, "Failed to load Sanctuary", exception);
            return null;
        }
    }

    private ExtendedButton infoButton(Sanctuary sanctuary, boolean adminMode) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>State: <white>" + sanctuary.state());
        lore.add("<gray>Owner: <white>" + mini(playerLabel(sanctuary.ownerId())));
        lore.add("<gray>Tier: <white>" + sanctuary.tier());
        lore.add("<gray>Generation: <white>" + sanctuary.anchorGeneration());
        lore.add("<gray>Radius: <white>" + String.format(Locale.ROOT, "%.2f", sanctuary.territoryRadius()) + " blocks");
        lore.add("<gray>Location: <white>" + mini(formatPosition(sanctuary.position())));
        if (adminMode) {
            lore.add("");
            lore.add("<dark_gray>ID: " + sanctuary.id());
            lore.add("<dark_gray>Owner UUID: " + sanctuary.ownerId());
            lore.add("<dark_gray>Debug ephemeral: " + sanctuary.debugEphemeral());
            lore.add("<dark_gray>Created: " + sanctuary.createdAt());
            lore.add("<dark_gray>Updated: " + sanctuary.updatedAt());
            sanctuary.destroyedAt().ifPresent(value -> lore.add("<red>Destroyed: " + value));
            sanctuary.destructionReason().ifPresent(value -> lore.add("<red>Reason: " + mini(value)));
        } else {
            lore.add("<dark_gray>ID: " + sanctuary.id().toString().substring(0, 8));
        }
        return button(Material.BEACON, "<gold>" + mini(sanctuary.name()), lore, null);
    }

    private static ExtendedButton button(
        Material material,
        String name,
        List<String> lore,
        java.util.function.Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> onClick
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

    private void menuError(Player player, String message, Exception exception) {
        player.sendMessage(ChatColor.RED + "Sanctuary UI could not complete that action.");
        plugin.getLogger().log(Level.SEVERE, message, exception);
    }

    static String normalizeSanctuaryName(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Sanctuary name cannot be blank.");
        }
        if (name.length() > 32) {
            throw new IllegalArgumentException("Sanctuary name cannot be longer than 32 characters.");
        }
        return name;
    }

    public static Optional<Sanctuary> resolveSelector(String selector, List<Sanctuary> candidates) {
        try {
            UUID id = UUID.fromString(selector);
            return candidates.stream().filter(value -> value.id().equals(id)).findFirst();
        } catch (IllegalArgumentException ignored) {
            return candidates.stream()
                .filter(value -> selectorLabel(value, candidates).equalsIgnoreCase(selector))
                .findFirst();
        }
    }

    public static String selectorLabel(Sanctuary sanctuary, List<Sanctuary> candidates) {
        String base = sanctuary.name().trim().replaceAll("\\s+", "_");
        long sameName = candidates.stream()
            .filter(value -> value.name().equalsIgnoreCase(sanctuary.name()))
            .count();
        if (sameName == 1) {
            return base;
        }
        String ownerName = Bukkit.getOfflinePlayer(sanctuary.ownerId()).getName();
        if (ownerName != null && !ownerName.isBlank()) {
            String ownerLabel = base + "@" + ownerName;
            long sameOwnerLabel = candidates.stream()
                .filter(value -> value.name().equalsIgnoreCase(sanctuary.name()))
                .filter(value -> value.ownerId().equals(sanctuary.ownerId()))
                .count();
            if (sameOwnerLabel == 1) {
                return ownerLabel;
            }
        }
        return base + "~" + sanctuary.id().toString().substring(0, 8);
    }

    private static String playerLabel(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name == null || name.isBlank()
            ? playerId.toString().substring(0, 8)
            : name;
    }

    private static String formatPosition(Optional<SanctuaryPosition> position) {
        if (position.isEmpty()) {
            return "none";
        }
        SanctuaryPosition value = position.orElseThrow();
        return value.world() + " " + value.x() + " " + value.y() + " " + value.z();
    }

    private static String effectDisplayName(SanctuaryEffect effect) {
        return switch (effect) {
            case REGENERATION -> "Regeneration";
            case RESISTANCE -> "Resistance";
            case STRENGTH -> "Strength";
            case HASTE -> "Haste";
            case SPEED -> "Speed";
            case ELYTRA_DISABLED -> "Elytra Disabled";
            case MINING_FATIGUE -> "Mining Fatigue";
            case WEAKNESS -> "Weakness";
            case BLINDNESS -> "Blindness";
            case WITHER -> "Wither";
        };
    }

    private static Material effectMaterial(SanctuaryEffect effect) {
        return switch (effect) {
            case REGENERATION -> Material.GHAST_TEAR;
            case RESISTANCE -> Material.IRON_CHESTPLATE;
            case STRENGTH -> Material.BLAZE_POWDER;
            case HASTE -> Material.GOLDEN_PICKAXE;
            case SPEED -> Material.SUGAR;
            case ELYTRA_DISABLED -> Material.ELYTRA;
            case MINING_FATIGUE -> Material.IRON_PICKAXE;
            case WEAKNESS -> Material.FERMENTED_SPIDER_EYE;
            case BLINDNESS -> Material.INK_SAC;
            case WITHER -> Material.WITHER_ROSE;
        };
    }

    private static String formatRadius(double radius) {
        return String.format(Locale.ROOT, "%.1f blocks", radius);
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

    private static String modeDisplayName(SanctuarySecurityMode mode) {
        return mode == SanctuarySecurityMode.LOCKDOWN ? "Lockdown" : "Normal";
    }

    private static String capabilityDisplayName(SanctuaryCapability capability) {
        String value = capability.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String mini(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static List<Integer> createListSlots() {
        List<Integer> result = new ArrayList<>();
        for (int slot = 9; slot <= 44; slot++) {
            result.add(slot);
        }
        return List.copyOf(result);
    }
}

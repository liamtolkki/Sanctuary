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
import dev.liamtolkkinen.sanctuary.sentry.SentryUiService;
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

/** Shared Sanctuary management UI. Anchor-local settings remain in AnchorUiService. */
public final class SanctuaryUiService {
    private static final List<Integer> LIST_SLOTS = createListSlots();

    private final SanctuaryPlugin plugin;
    private final ExtendedUI ui;
    private final SanctuaryRepository repository;
    private final SanctuaryPermissionService permissionService;
    private final SanctuarySecurityService securityService;
    @SuppressWarnings("unused")
    private final SanctuaryEffectService effectService;
    private final TerritoryBoundaryService boundaryService;
    private final SentryUiService sentryUiService;

    public SanctuaryUiService(
        SanctuaryPlugin plugin,
        ExtendedUI ui,
        SanctuaryRepository repository,
        SanctuaryPermissionService permissionService,
        SanctuarySecurityService securityService,
        SanctuaryEffectService effectService,
        TerritoryBoundaryService boundaryService,
        SentryUiService sentryUiService
    ) {
        this.plugin = plugin;
        this.ui = ui;
        this.repository = repository;
        this.permissionService = permissionService;
        this.securityService = securityService;
        this.effectService = effectService;
        this.boundaryService = boundaryService;
        this.sentryUiService = sentryUiService;
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
            super(6, adminMode ? "<dark_red>Sanctuary Admin" : "<gold>Sanctuary");
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        public String title(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) return "<red>Sanctuary unavailable";
            return (adminMode ? "<dark_red>Admin | " : "<gold>") + mini(sanctuary.name());
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(22, button(Material.BARRIER, "<red>Sanctuary unavailable", List.of(), click -> click.menu().close()));
                return;
            }

            menu.set(4, infoButton(sanctuary, adminMode));

            try {
                SanctuarySecurityMode mode = securityService.mode(sanctuary);
                boolean lockdownUnlocked = adminMode || SanctuarySecurityService.lockdownUnlocked(sanctuary);
                menu.set(10, lockdownButton(sanctuary, mode, lockdownUnlocked));
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load Sanctuary security mode", exception);
            }

            menu.set(12, button(
                Material.COMPARATOR,
                "<yellow>Sentry Behavior",
                List.of(
                    "<gray>Direct access to Sanctuary-wide sentry defaults.",
                    "<gray>Individual sentries can still override them."
                ),
                click -> sentryUiService.openBehavior(click.player(), sanctuary)
            ));

            menu.set(14, button(
                Material.ARMOR_STAND,
                "<gold>Sentry Posts",
                List.of(
                    "<gray>View every registered sentry post.",
                    "<gray>Edit individual behavior, recall, or disable."
                ),
                click -> sentryUiService.openPosts(click.player(), sanctuary)
            ));

            menu.set(16, button(
                sanctuary.state() == SanctuaryState.ACTIVE ? Material.ENDER_EYE : Material.GRAY_DYE,
                "<aqua>Show Boundary",
                sanctuary.state() == SanctuaryState.ACTIVE
                    ? List.of("<gray>Display the full Sanctuary perimeter only to you.")
                    : List.of("<dark_gray>Unavailable while " + sanctuary.state() + "."),
                click -> showBoundary(click.player(), sanctuary)
            ));

            menu.set(20, button(
                Material.LIME_DYE,
                "<green>Trusted Players",
                List.of(
                    "<gray>Manage trusted players and their capabilities.",
                    "<gray>Trusted players remain safe during Lockdown."
                ),
                click -> click.menu().open(new TrustMenu(sanctuary.id(), adminMode))
            ));

            menu.set(22, button(
                Material.RED_DYE,
                "<red>Blacklist",
                List.of("<gray>Manage players who are always treated as hostile."),
                click -> click.menu().open(new BlacklistMenu(sanctuary.id(), adminMode))
            ));

            menu.set(24, button(
                Material.SHIELD,
                "<red>Security Details",
                List.of(
                    "<gray>Review threat policy, hard protections,",
                    "<gray>relationship colors, and current security state."
                ),
                click -> click.menu().open(new SecurityMenu(sanctuary.id(), adminMode))
            ));

            menu.set(31, button(
                Material.WRITABLE_BOOK,
                "<yellow>Sanctuary Settings",
                List.of(
                    "<gray>Rename the Sanctuary and access lower-frequency",
                    "<gray>shared management actions."
                ),
                click -> click.menu().open(new SettingsMenu(sanctuary.id(), adminMode))
            ));

            if (adminMode && sanctuary.debugEphemeral()) {
                menu.set(33, button(
                    Material.COMMAND_BLOCK,
                    "<light_purple>Debug Tools",
                    List.of("<gray>Relationship and permission controls for testing."),
                    click -> click.menu().open(new DebugToolsMenu(sanctuary.id()))
                ));
            } else if (adminMode) {
                menu.set(33, button(
                    Material.PAPER,
                    "<yellow>Admin View",
                    List.of("<gray>You are inspecting this Sanctuary as an admin."),
                    null
                ));
            }

            menu.set(49, StandardButtons.close(context.theme()));
        }
    }

    private final class SettingsMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private SettingsMenu(UUID sanctuaryId, boolean adminMode) {
            super(3, "<yellow>Sanctuary Settings");
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

            boolean owner = sanctuary.ownerId().equals(context.player().getUniqueId());
            menu.set(10, button(
                owner ? Material.NAME_TAG : Material.GRAY_DYE,
                owner ? "<yellow>Rename Sanctuary" : "<dark_gray>Rename Sanctuary",
                List.of(
                    "<gray>Current name: <white>" + mini(sanctuary.name()),
                    owner ? "<yellow>Click to choose a new name." : "<dark_gray>Only the owner can rename it."
                ),
                owner ? click -> showRenameDialog(click.menu(), sanctuary, adminMode) : null
            ));

            menu.set(13, button(
                sanctuary.state() == SanctuaryState.ACTIVE ? Material.ENDER_EYE : Material.GRAY_DYE,
                "<aqua>Show Boundary",
                sanctuary.state() == SanctuaryState.ACTIVE
                    ? List.of("<gray>Display the full Sanctuary perimeter only to you.")
                    : List.of("<dark_gray>Unavailable while " + sanctuary.state() + "."),
                click -> showBoundary(click.player(), sanctuary)
            ));

            menu.set(16, button(
                Material.SHIELD,
                "<red>Security Details",
                List.of("<gray>Open the detailed security status screen."),
                click -> click.menu().open(new SecurityMenu(sanctuary.id(), adminMode))
            ));

            menu.set(18, StandardButtons.back(context.theme()));
            menu.set(26, StandardButtons.close(context.theme()));
        }
    }

    private final class SecurityMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private SecurityMenu(UUID sanctuaryId, boolean adminMode) {
            super(4, "<red>Security Details");
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
                    sanctuary,
                    context.player().getUniqueId()
                );
                SanctuaryThreat threat = securityService.threat(
                    sanctuary,
                    context.player().getUniqueId()
                );
                boolean lockdownUnlocked = adminMode || SanctuarySecurityService.lockdownUnlocked(sanctuary);

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

                menu.set(11, lockdownButton(sanctuary, mode, lockdownUnlocked));

                menu.set(13, button(
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
                            "<gray>Ordinary actions are not physically blocked.",
                            "<gray>Defense comes from anchor effects and sentries."
                        ),
                    null
                ));

                menu.set(15, button(
                    Material.WHITE_DYE,
                    "<white>Boundary Threat Colors",
                    List.of(
                        "<gray>Normal territory boundary uses relationship particles.",
                        "<white>Anchor Proximity: white when safe, red when hostile.",
                        "<gray>Anchor Proximity is enabled by a Watcher's Eye."
                    ),
                    null
                ));

                menu.set(22, button(
                    Material.BEACON,
                    "<aqua>Anchor Effects",
                    List.of(
                        "<gray>Effects are configured on each individual anchor.",
                        "<gray>Right-click a Beacon or Conduit to manage its effects.",
                        "<gray>Conduit effects require water contact or rain."
                    ),
                    null
                ));
            } catch (SQLException exception) {
                menuError(context.player(), "Failed to load Sanctuary security", exception);
            }

            menu.set(27, StandardButtons.back(context.theme()));
            menu.set(35, StandardButtons.close(context.theme()));
        }
    }

    private ExtendedButton lockdownButton(
        Sanctuary sanctuary,
        SanctuarySecurityMode mode,
        boolean unlocked
    ) {
        if (!unlocked) {
            return button(
                Material.IRON_BARS,
                "<dark_gray>Lockdown Locked",
                List.of(
                    "<gray>Reach Sanctuary Tier " + roman(SanctuarySecurityService.LOCKDOWN_UNLOCK_TIER)
                        + " to unlock Lockdown."
                ),
                null
            );
        }

        boolean enabled = mode == SanctuarySecurityMode.LOCKDOWN;
        return button(
            enabled ? Material.REDSTONE_TORCH : Material.LEVER,
            enabled ? "<red>Lockdown: Enabled" : "<yellow>Lockdown: Available",
            List.of(
                enabled
                    ? "<red>Neutral outsiders are currently hostile."
                    : "<gray>Neutral outsiders are currently neutral.",
                "<yellow>Click to switch to " + (enabled ? "Normal" : "Lockdown") + "."
            ),
            click -> toggleLockdown(click.player(), sanctuary.id(), click.menu())
        );
    }

    private void toggleLockdown(Player player, UUID sanctuaryId, ExtendedMenuContext context) {
        try {
            Sanctuary sanctuary = repository.findById(sanctuaryId).orElse(null);
            if (sanctuary == null) {
                player.sendMessage(ChatColor.RED + "That Sanctuary no longer exists.");
                return;
            }
            boolean admin = player.hasPermission("sanctuary.admin");
            if (!admin && !SanctuarySecurityService.lockdownUnlocked(sanctuary)) {
                player.sendMessage(
                    ChatColor.RED + "Lockdown unlocks at Sanctuary Tier "
                        + SanctuarySecurityService.LOCKDOWN_UNLOCK_TIER + "."
                );
                return;
            }
            SanctuarySecurityMode current = securityService.mode(sanctuary);
            SanctuarySecurityMode next = current == SanctuarySecurityMode.NORMAL
                ? SanctuarySecurityMode.LOCKDOWN
                : SanctuarySecurityMode.NORMAL;
            securityService.setMode(sanctuary, next);
            player.sendMessage(ChatColor.YELLOW + "Security mode set to " + modeDisplayName(next) + ".");
            context.refresh();
        } catch (SQLException exception) {
            menuError(player, "Failed to update security mode", exception);
        }
    }

    private final class TrustMenu extends ExtendedPagedMenu<SanctuaryTrustEntry> {
        private final UUID sanctuaryId;
        private final boolean adminMode;

        private TrustMenu(UUID sanctuaryId, boolean adminMode) {
            super(6, "<green>Trusted Players", LIST_SLOTS, 47, 51);
            this.sanctuaryId = sanctuaryId;
            this.adminMode = adminMode;
        }

        @Override
        protected List<SanctuaryTrustEntry> items(ExtendedMenuContext context) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) return List.of();
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
                "<green>" + mini(playerLabel(entry.playerId())),
                lore,
                click -> click.menu().open(new CapabilityMenu(sanctuaryId, adminMode, entry.playerId()))
            );
        }

        @Override
        protected void buildStatic(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            Sanctuary sanctuary = loadForMenu(context.player(), sanctuaryId, adminMode);
            if (sanctuary == null) {
                menu.set(53, StandardButtons.close(context.theme()));
                return;
            }
            menu.set(4, button(
                Material.GOLDEN_HELMET,
                "<gold>Owner: " + mini(playerLabel(sanctuary.ownerId())),
                List.of("<green>The owner always has all capabilities."),
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
            if (sanctuary == null) return List.of();
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
                    if (sanctuary == null) return;
                    try {
                        securityService.prepareForTrust(sanctuary, target.getUniqueId());
                        permissionService.trust(sanctuary, target.getUniqueId(), Instant.now());
                        click.player().sendMessage(
                            ChatColor.GREEN + "Trusted " + target.getName() + " in " + sanctuary.name() + "."
                        );
                        click.menu().open(new CapabilityMenu(sanctuaryId, adminMode, target.getUniqueId()));
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
            if (sanctuary == null) return List.of();
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
                    if (sanctuary == null) return;
                    try {
                        securityService.unblacklist(sanctuary, entry.playerId());
                        click.player().sendMessage(
                            ChatColor.YELLOW + "Removed " + playerLabel(entry.playerId()) + " from the blacklist."
                        );
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
            if (sanctuary == null) return List.of();
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
                        .lore(
                            "<gray>Click to blacklist this player.",
                            "<yellow>If trusted, trust will be removed."
                        )
                        .build()
                )
                .onClick(click -> {
                    Sanctuary sanctuary = loadForMenu(click.player(), sanctuaryId, adminMode);
                    if (sanctuary == null) return;
                    try {
                        securityService.blacklist(sanctuary, target.getUniqueId(), Instant.now());
                        click.player().sendMessage(
                            ChatColor.RED + "Blacklisted " + target.getName() + " in " + sanctuary.name() + "."
                        );
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
                menu.set(
                    10 + index,
                    button(
                        allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                        (allowed ? "<green>" : "<red>") + capabilityDisplayName(capability),
                        owner
                            ? List.of("<green>ALLOWED", "<dark_gray>The owner always has this capability.")
                            : List.of(
                                allowed ? "<green>ALLOWED" : "<red>DENIED",
                                "<yellow>Click to " + (allowed ? "deny" : "allow") + "."
                            ),
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

            menu.set(27, StandardButtons.back(context.theme()));
            menu.set(35, StandardButtons.close(context.theme()));
        }
    }

    private final class DebugToolsMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;

        private DebugToolsMenu(UUID sanctuaryId) {
            super(3, "<light_purple>Debug Tools");
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

            menu.set(11, button(
                Material.PLAYER_HEAD,
                "<light_purple>My Debug Relationship",
                List.of("<gray>Switch yourself between Trusted, Neutral, and Blacklisted."),
                click -> click.menu().open(new DebugRelationshipMenu(sanctuaryId))
            ));
            menu.set(15, button(
                Material.COMMAND_BLOCK,
                "<light_purple>My Debug Permissions",
                List.of("<gray>Edit your own hard-protection capabilities."),
                click -> click.menu().open(
                    new CapabilityMenu(sanctuaryId, true, click.player().getUniqueId())
                )
            ));
            menu.set(18, StandardButtons.back(context.theme()));
            menu.set(26, StandardButtons.close(context.theme()));
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
                }
                case OWNER -> throw new IllegalArgumentException("Debug users cannot become the synthetic owner.");
            }
            player.sendMessage(ChatColor.YELLOW + "Debug relationship set to " + relationship + ".");
            context.refresh();
        } catch (SQLException | IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    private void toggleCapability(
        Player player,
        Sanctuary sanctuary,
        UUID targetPlayerId,
        SanctuaryCapability capability,
        ExtendedMenuContext context
    ) {
        try {
            Set<SanctuaryCapability> effective = permissionService.effectiveCapabilities(
                sanctuary,
                targetPlayerId
            );
            boolean currentlyAllowed = effective.contains(capability);
            if (!currentlyAllowed && !permissionService.isTrusted(sanctuary, targetPlayerId)) {
                securityService.prepareForTrust(sanctuary, targetPlayerId);
                permissionService.trust(sanctuary, targetPlayerId, Instant.now());
            }
            permissionService.setCapability(
                sanctuary,
                targetPlayerId,
                capability,
                !currentlyAllowed
            );
            context.refresh();
        } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
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

            repository.save(new Sanctuary(
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

    private void showBoundary(Player player, Sanctuary sanctuary) {
        if (sanctuary.state() != SanctuaryState.ACTIVE) {
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
            if (sanctuary == null) return null;
            if (adminMode) return player.hasPermission("sanctuary.admin") ? sanctuary : null;
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
        lore.add("<gray>Shared progression tier: <white>" + roman(sanctuary.tier()));
        lore.add("<gray>Anchor generation: <white>" + sanctuary.anchorGeneration());
        lore.add("<gray>Legacy radius summary: <white>" + String.format(
            Locale.ROOT,
            "%.2f",
            sanctuary.territoryRadius()
        ) + " blocks");
        lore.add("<gray>Compatibility location: <white>" + mini(formatPosition(sanctuary.position())));
        if (adminMode) {
            lore.add("");
            lore.add("<dark_gray>ID: " + sanctuary.id());
            lore.add("<dark_gray>Owner UUID: " + sanctuary.ownerId());
            lore.add("<dark_gray>Debug ephemeral: " + sanctuary.debugEphemeral());
            lore.add("<dark_gray>Created: " + sanctuary.createdAt());
            lore.add("<dark_gray>Updated: " + sanctuary.updatedAt());
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
            if (!lore.isEmpty()) builder.lore(lore.toArray(String[]::new));
            return builder.build();
        };
        ExtendedButton.Builder result = ExtendedButton.builder(provider);
        if (onClick != null) result.onClick(onClick);
        return result.build();
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
        if (sameName == 1) return base;

        String ownerName = Bukkit.getOfflinePlayer(sanctuary.ownerId()).getName();
        if (ownerName != null && !ownerName.isBlank()) {
            String ownerLabel = base + "@" + ownerName;
            long sameOwnerLabel = candidates.stream()
                .filter(value -> value.name().equalsIgnoreCase(sanctuary.name()))
                .filter(value -> value.ownerId().equals(sanctuary.ownerId()))
                .count();
            if (sameOwnerLabel == 1) return ownerLabel;
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
        if (position.isEmpty()) return "none";
        SanctuaryPosition value = position.orElseThrow();
        return value.world() + " " + value.x() + " " + value.y() + " " + value.z();
    }

    private static String modeDisplayName(SanctuarySecurityMode mode) {
        return mode == SanctuarySecurityMode.LOCKDOWN ? "Lockdown" : "Normal";
    }

    private static String capabilityDisplayName(SanctuaryCapability capability) {
        String value = capability.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

    private static String mini(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static List<Integer> createListSlots() {
        List<Integer> result = new ArrayList<>();
        for (int slot = 9; slot <= 44; slot++) result.add(slot);
        return List.copyOf(result);
    }
}

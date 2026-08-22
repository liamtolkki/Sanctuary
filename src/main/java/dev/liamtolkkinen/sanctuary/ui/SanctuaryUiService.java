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
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
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
    private final TerritoryBoundaryService boundaryService;

    public SanctuaryUiService(
        SanctuaryPlugin plugin,
        ExtendedUI ui,
        SanctuaryRepository repository,
        SanctuaryPermissionService permissionService,
        TerritoryBoundaryService boundaryService
    ) {
        this.plugin = plugin;
        this.ui = ui;
        this.repository = repository;
        this.permissionService = permissionService;
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
                "<gold>Trust & Capabilities",
                List.of("<gray>Manage trusted players and exactly", "<gray>what they can do here."),
                click -> click.menu().open(new TrustMenu(sanctuary.id(), adminMode))
            ));

            if (adminMode && sanctuary.debugEphemeral()) {
                menu.set(24, button(
                    Material.COMMAND_BLOCK,
                    "<light_purple>My Debug Permissions",
                    List.of("<gray>Toggle your own permissions for", "<gray>solo protection testing."),
                    click -> click.menu().open(
                        new CapabilityMenu(sanctuary.id(), true, click.player().getUniqueId())
                    )
                ));
            } else if (adminMode) {
                menu.set(24, button(
                    Material.PAPER,
                    "<yellow>Admin View",
                    List.of("<gray>You are inspecting this Sanctuary as an admin."),
                    null
                ));
            }

            menu.set(40, StandardButtons.close(context.theme()));
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

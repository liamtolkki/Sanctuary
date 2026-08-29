package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedItemBuilder;
import dev.liamtolkkinen.extendedui.ExtendedItemProvider;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.extendedui.StandardButtons;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SentryUiService {
    private final JavaPlugin plugin;
    private final ExtendedUI ui;
    private final SanctuaryRepository sanctuaryRepository;
    private final SentryRepository repository;
    private final SentryService service;
    private final Logger logger;

    public SentryUiService(
        JavaPlugin plugin,
        ExtendedUI ui,
        SanctuaryRepository sanctuaryRepository,
        SentryRepository repository,
        SentryService service,
        Logger logger
    ) {
        this.plugin = plugin;
        this.ui = ui;
        this.sanctuaryRepository = sanctuaryRepository;
        this.repository = repository;
        this.service = service;
        this.logger = logger;
    }

    public void openDashboard(Player player, Sanctuary sanctuary) {
        openPosts(player, sanctuary);
    }

    public void openPosts(Player player, Sanctuary sanctuary) {
        if (!service.canManage(player, sanctuary)) {
            player.sendMessage(ChatColor.RED + "Only the Sanctuary owner can manage sentries.");
            return;
        }
        ui.open(player, new DashboardMenu(sanctuary.id()));
    }

    public void openBehavior(Player player, Sanctuary sanctuary) {
        if (!service.canManage(player, sanctuary)) {
            player.sendMessage(ChatColor.RED + "Only the Sanctuary owner can manage sentries.");
            return;
        }
        ui.open(player, new DefaultsMenu(sanctuary.id()));
    }

    public void open(Player player, Sanctuary sanctuary, SentryRecord record) {
        if (!service.canManage(player, sanctuary)) return;
        ui.open(player, new SentryMenu(sanctuary.id(), record.id()));
    }

    private final class DashboardMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;

        DashboardMenu(UUID sanctuaryId) {
            super(6, "<gold>Sentry Posts");
            this.sanctuaryId = sanctuaryId;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            try {
                Sanctuary sanctuary = sanctuaryRepository.findById(sanctuaryId).orElse(null);
                if (sanctuary == null || !service.canManage(context.player(), sanctuary)) {
                    menu.set(22, button(Material.BARRIER, "<red>Unavailable", List.of(), null));
                    return;
                }

                List<SentryRecord> sentries = repository.findBySanctuary(sanctuaryId);
                menu.set(4, button(
                    Material.ARMOR_STAND,
                    "<gold>Registered Sentries: " + sentries.size(),
                    List.of(
                        "<gray>Select a sentry below for individual behavior,",
                        "<gray>recall, enable, and disable controls."
                    ),
                    null
                ));

                int[] slots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
                };
                for (int i = 0; i < Math.min(slots.length, sentries.size()); i++) {
                    SentryRecord sentry = sentries.get(i);
                    SentryDefinition definition = service.definition(sentry).orElse(null);
                    if (definition == null) continue;
                    List<String> lore = new ArrayList<>();
                    lore.add("<gray>Status: <white>" + sentry.state());
                    lore.add("<gray>Range: <white>" + definition.targetRadius() + " blocks");
                    lore.add("<gray>Post: <white>" + sentry.x() + ", " + sentry.y() + ", " + sentry.z());
                    if (sentry.state() == SentryState.DOWN && sentry.respawnAt().isPresent()) {
                        lore.add("<yellow>Respawn in: " + Math.max(
                            0,
                            Duration.between(Instant.now(), sentry.respawnAt().orElseThrow()).toSeconds()
                        ) + "s");
                    }
                    menu.set(
                        slots[i],
                        button(
                            sentry.state() == SentryState.DISABLED ? Material.GRAY_DYE : Material.ARMOR_STAND,
                            "<yellow>" + definition.displayName(),
                            lore,
                            click -> click.menu().open(new SentryMenu(sanctuaryId, sentry.id()))
                        )
                    );
                }
                if (sentries.size() > slots.length) {
                    menu.set(49, button(
                        Material.PAPER,
                        "<yellow>More sentries exist",
                        List.of("<gray>Showing first " + slots.length + " sentries."),
                        null
                    ));
                }
            } catch (SQLException exception) {
                error(context.player(), exception);
            }
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private final class DefaultsMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;

        DefaultsMenu(UUID sanctuaryId) {
            super(5, "<gold>Sentry Behavior");
            this.sanctuaryId = sanctuaryId;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            try {
                Sanctuary sanctuary = sanctuaryRepository.findById(sanctuaryId).orElse(null);
                if (sanctuary == null || !service.canManage(context.player(), sanctuary)) return;

                menu.set(4, button(
                    Material.COMPARATOR,
                    "<gold>Sanctuary Sentry Defaults",
                    List.of(
                        "<gray>These settings apply to every sentry that",
                        "<gray>has the corresponding trigger set to Inherit."
                    ),
                    null
                ));

                int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};
                int index = 0;
                for (SentryTrigger trigger : SentryTrigger.values()) {
                    boolean enabled = repository.getDefault(sanctuaryId, trigger);
                    menu.set(
                        slots[index++],
                        button(
                            enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                            (enabled ? "<green>" : "<gray>") + trigger.displayName(),
                            List.of(
                                "<gray>Default: <white>" + (enabled ? "ON" : "OFF"),
                                "<yellow>Click to toggle"
                            ),
                            click -> {
                                try {
                                    repository.setDefault(sanctuaryId, trigger, !enabled);
                                    refreshNextTick(click.menu());
                                } catch (SQLException exception) {
                                    error(click.player(), exception);
                                }
                            }
                        )
                    );
                }
            } catch (SQLException exception) {
                error(context.player(), exception);
            }
            menu.set(36, StandardButtons.back(context.theme()));
            menu.set(44, StandardButtons.close(context.theme()));
        }
    }

    private final class SentryMenu extends ExtendedInventoryMenu {
        private final UUID sanctuaryId;
        private final UUID sentryId;

        SentryMenu(UUID sanctuaryId, UUID sentryId) {
            super(6, "<yellow>Sentry");
            this.sanctuaryId = sanctuaryId;
            this.sentryId = sentryId;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();
            try {
                Sanctuary sanctuary = sanctuaryRepository.findById(sanctuaryId).orElse(null);
                SentryRecord sentry = repository.findById(sentryId).orElse(null);
                if (sanctuary == null || sentry == null || !service.canManage(context.player(), sanctuary)) return;

                SentryDefinition definition = service.definition(sentry).orElseThrow();
                menu.set(4, button(
                    Material.ARMOR_STAND,
                    "<gold>" + definition.displayName(),
                    List.of(
                        "<gray>Status: <white>" + sentry.state(),
                        "<gray>Target radius: <white>" + definition.targetRadius(),
                        "<gray>Home: <white>" + sentry.x() + ", " + sentry.y() + ", " + sentry.z()
                    ),
                    null
                ));

                int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};
                int index = 0;
                for (SentryTrigger trigger : SentryTrigger.values()) {
                    SentryOverride override = repository.getOverride(sentryId, trigger);
                    boolean effective = service.effective(sentry, trigger);
                    String state = override == SentryOverride.INHERIT
                        ? "INHERIT (" + (effective ? "ON" : "OFF") + ")"
                        : override.name();
                    menu.set(
                        slots[index++],
                        button(
                            effective ? Material.LIME_DYE : Material.GRAY_DYE,
                            "<yellow>" + trigger.displayName(),
                            List.of(
                                "<gray>Setting: <white>" + state,
                                "<gray>Cycle: Inherit -> On -> Off"
                            ),
                            click -> {
                                try {
                                    repository.setOverride(sentryId, trigger, override.next());
                                    refreshNextTick(click.menu());
                                } catch (SQLException exception) {
                                    error(click.player(), exception);
                                }
                            }
                        )
                    );
                }

                menu.set(38, button(
                    Material.COMPASS,
                    "<aqua>Recall",
                    List.of(
                        "<gray>Clear target and pathfind home.",
                        "<gray>Teleports only if not home after 15 seconds."
                    ),
                    click -> {
                        try {
                            service.recall(repository.findById(sentryId).orElseThrow());
                            refreshNextTick(click.menu());
                        } catch (SQLException exception) {
                            error(click.player(), exception);
                        }
                    }
                ));

                boolean disabled = sentry.state() == SentryState.DISABLED;
                menu.set(42, button(
                    disabled ? Material.LIME_DYE : Material.RED_DYE,
                    disabled ? "<green>Enable Sentry" : "<red>Disable Sentry",
                    List.of(
                        disabled
                            ? "<gray>Resume normal sentry behavior."
                            : "<gray>Stops targeting and marks the sentry disabled."
                    ),
                    click -> {
                        try {
                            service.setDisabled(repository.findById(sentryId).orElseThrow(), !disabled);
                            refreshNextTick(click.menu());
                        } catch (SQLException exception) {
                            error(click.player(), exception);
                        }
                    }
                ));
            } catch (SQLException exception) {
                error(context.player(), exception);
            }
            menu.set(45, StandardButtons.back(context.theme()));
            menu.set(53, StandardButtons.close(context.theme()));
        }
    }

    private void refreshNextTick(ExtendedMenuContext menu) {
        Bukkit.getScheduler().runTask(plugin, menu::refresh);
    }

    private static ExtendedButton button(
        Material material,
        String name,
        List<String> lore,
        java.util.function.Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> onClick
    ) {
        ExtendedItemProvider provider = () -> {
            var builder = ExtendedItemBuilder.of(material).name(name);
            if (!lore.isEmpty()) builder.lore(lore.toArray(String[]::new));
            return builder.build();
        };
        var button = ExtendedButton.builder(provider);
        if (onClick != null) button.onClick(onClick);
        return button.build();
    }

    private void error(Player player, Exception exception) {
        player.sendMessage(ChatColor.RED + "Sanctuary sentry UI failed.");
        logger.log(Level.SEVERE, "Sentry UI failure", exception);
    }
}

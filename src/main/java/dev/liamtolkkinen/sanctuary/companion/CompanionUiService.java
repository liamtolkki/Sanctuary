package dev.liamtolkkinen.sanctuary.companion;

import dev.liamtolkkinen.extendedui.ExtendedButton;
import dev.liamtolkkinen.extendedui.ExtendedInventoryMenu;
import dev.liamtolkkinen.extendedui.ExtendedItemBuilder;
import dev.liamtolkkinen.extendedui.ExtendedItemProvider;
import dev.liamtolkkinen.extendedui.ExtendedMenuBuilder;
import dev.liamtolkkinen.extendedui.ExtendedMenuContext;
import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.extendedui.StandardButtons;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompanionUiService {
    private final JavaPlugin plugin;
    private final ExtendedUI ui;
    private final CompanionService service;
    private final CompanionEggState eggState;
    private final Logger logger;

    public CompanionUiService(
        JavaPlugin plugin,
        ExtendedUI ui,
        CompanionService service,
        CompanionEggState eggState,
        Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.service = Objects.requireNonNull(service, "service");
        this.eggState = Objects.requireNonNull(eggState, "eggState");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player, Mob companion) {
        if (!service.isManaged(companion) || !service.isOwner(player, companion)) {
            return;
        }
        ui.open(player, new CompanionMenu(companion.getUniqueId()));
    }

    private final class CompanionMenu extends ExtendedInventoryMenu {
        private final UUID entityId;

        private CompanionMenu(UUID entityId) {
            super(3, "<gold>Companion");
            this.entityId = entityId;
        }

        @Override
        public void build(ExtendedMenuContext context, ExtendedMenuBuilder menu) {
            menu.fillBackground();

            Mob companion = companion(context.player());
            if (companion == null) {
                menu.set(13, button(
                    Material.BARRIER,
                    "<red>Companion unavailable",
                    List.of("<gray>This companion is no longer available."),
                    click -> click.menu().close()
                ));
                return;
            }

            CompanionDefinition definition = service.definition(companion).orElse(null);
            if (definition == null) {
                menu.set(13, button(
                    Material.BARRIER,
                    "<red>Unknown companion",
                    List.of(),
                    click -> click.menu().close()
                ));
                return;
            }

            AttributeInstance maxHealthAttribute = companion.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = maxHealthAttribute == null
                ? companion.getHealth()
                : maxHealthAttribute.getValue();
            CompanionMode mode = service.mode(companion);

            menu.set(4, button(
                Material.NAME_TAG,
                "<gold>" + definition.displayName(),
                List.of(
                    "<gray>Health: <white>" + health(companion.getHealth()) + " / " + health(maxHealth),
                    "<gray>Mode: <white>" + mode.name()
                ),
                null
            ));

            menu.set(11, button(
                mode == CompanionMode.FOLLOW ? Material.LIME_DYE : Material.COMPASS,
                mode == CompanionMode.FOLLOW ? "<green>Following" : "<yellow>Follow",
                List.of(
                    mode == CompanionMode.FOLLOW
                        ? "<gray>This companion is already following you."
                        : "<gray>Follow you when you move too far away."
                ),
                click -> {
                    Mob current = companion(click.player());
                    if (current == null) {
                        click.menu().close();
                        return;
                    }
                    service.setMode(current, CompanionMode.FOLLOW);
                    refreshNextTick(click.menu());
                }
            ));

            menu.set(13, button(
                mode == CompanionMode.STAY ? Material.LIME_DYE : Material.LEAD,
                mode == CompanionMode.STAY ? "<green>Staying" : "<yellow>Stay",
                List.of(
                    mode == CompanionMode.STAY
                        ? "<gray>This companion is already staying here."
                        : "<gray>Stay at its current position."
                ),
                click -> {
                    Mob current = companion(click.player());
                    if (current == null) {
                        click.menu().close();
                        return;
                    }
                    service.setMode(current, CompanionMode.STAY);
                    refreshNextTick(click.menu());
                }
            ));

            menu.set(15, button(
                Material.EGG,
                "<aqua>Pick Up Companion",
                List.of(
                    "<gray>Returns this companion to its egg.",
                    "<gray>Current damage is preserved."
                ),
                click -> {
                    Mob current = companion(click.player());
                    if (current == null) {
                        click.menu().close();
                        return;
                    }
                    CompanionDefinition currentDefinition = service.definition(current).orElse(null);
                    if (currentDefinition == null) {
                        click.menu().close();
                        return;
                    }

                    var egg = eggState.createPickupEgg(current, currentDefinition);
                    var overflow = click.player().getInventory().addItem(egg);
                    for (var item : overflow.values()) {
                        click.player().getWorld().dropItemNaturally(click.player().getLocation(), item);
                    }

                    service.removeCompanion(current);
                    current.remove();
                    click.menu().close();
                }
            ));

            menu.set(26, StandardButtons.close(context.theme()));
        }

        private Mob companion(Player player) {
            Entity entity = Bukkit.getEntity(entityId);
            if (!(entity instanceof Mob mob)
                || !service.isManaged(mob)
                || !service.isOwner(player, mob)) {
                return null;
            }
            return mob;
        }
    }

    private void refreshNextTick(ExtendedMenuContext menu) {
        Bukkit.getScheduler().runTask(plugin, menu::refresh);
    }

    private static String health(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static ExtendedButton button(
        Material material,
        String name,
        List<String> lore,
        java.util.function.Consumer<dev.liamtolkkinen.extendedui.ExtendedClickContext> onClick
    ) {
        ExtendedItemProvider provider = () -> {
            var builder = ExtendedItemBuilder.of(material).name(name);
            if (!lore.isEmpty()) {
                builder.lore(lore.toArray(String[]::new));
            }
            return builder.build();
        };
        var builder = ExtendedButton.builder(provider);
        if (onClick != null) {
            builder.onClick(onClick);
        }
        return builder.build();
    }
}

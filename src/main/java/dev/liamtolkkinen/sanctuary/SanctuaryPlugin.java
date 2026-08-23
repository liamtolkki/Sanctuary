package dev.liamtolkkinen.sanctuary;

import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorLifecycleService;
import dev.liamtolkkinen.sanctuary.anchor.DebugBeaconRegistrationService;
import dev.liamtolkkinen.sanctuary.api.DefaultSanctuaryApi;
import dev.liamtolkkinen.sanctuary.api.SanctuaryApi;
import dev.liamtolkkinen.sanctuary.command.SanctuaryCommand;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectService;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectTask;
import dev.liamtolkkinen.sanctuary.persistence.Database;
import dev.liamtolkkinen.sanctuary.protection.ElytraSuppressionListener;
import dev.liamtolkkinen.sanctuary.protection.SanctuaryProtectionListener;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.security.SanctuaryPermissionService;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.sentry.SentryListener;
import dev.liamtolkkinen.sanctuary.sentry.SentryRepository;
import dev.liamtolkkinen.sanctuary.sentry.SentryService;
import dev.liamtolkkinen.sanctuary.sentry.SentryTask;
import dev.liamtolkkinen.sanctuary.sentry.SentryUiService;
import dev.liamtolkkinen.sanctuary.territory.SanctuaryBoundaryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import dev.liamtolkkinen.sanctuary.trust.TrustRepository;
import dev.liamtolkkinen.sanctuary.ui.SanctuaryUiListener;
import dev.liamtolkkinen.sanctuary.ui.SanctuaryUiService;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctuaryPlugin extends JavaPlugin {
    private Database database;
    private SanctuaryApi sanctuaryApi;
    private ExtendedUI extendedUi;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Path databasePath = getDataFolder().toPath().resolve("sanctuary.db");
            database = new Database(databasePath);
            database.initialize();

            SanctuaryRepository repository = new SanctuaryRepository(database, getLogger());
            TrustRepository trustRepository = new TrustRepository(database);
            SentryRepository sentryRepository = new SentryRepository(database);
            SanctuaryPermissionService permissionService = new SanctuaryPermissionService(repository, trustRepository);
            SanctuarySecurityService securityService = new SanctuarySecurityService(repository, trustRepository);
            TerritoryPresenceService territoryPresenceService = new TerritoryPresenceService();
            SanctuaryBoundaryService boundaryService = new SanctuaryBoundaryService(
                repository,
                territoryPresenceService,
                this::getMaximumTerritoryRadius,
                getLogger()
            );
            AnchorItemService anchorItemService = new AnchorItemService(this);
            SanctuaryEffectService effectService = new SanctuaryEffectService(
                this,
                repository,
                securityService,
                territoryPresenceService,
                this::getMaximumTerritoryRadius,
                getLogger()
            );
            SentryService sentryService = new SentryService(
                this,
                repository,
                sentryRepository,
                securityService,
                territoryPresenceService,
                getLogger()
            );

            sanctuaryApi = new DefaultSanctuaryApi(repository, permissionService, securityService);
            getServer().getServicesManager().register(
                SanctuaryApi.class,
                sanctuaryApi,
                this,
                ServicePriority.Normal
            );

            getServer().getPluginManager().registerEvents(
                new SanctuaryProtectionListener(
                    repository,
                    permissionService,
                    securityService,
                    territoryPresenceService,
                    sentryService,
                    this::getMaximumTerritoryRadius,
                    getLogger()
                ),
                this
            );

            AnchorLifecycleService lifecycleService = new AnchorLifecycleService(
                repository,
                anchorItemService,
                boundaryService,
                getLogger()
            );
            getServer().getPluginManager().registerEvents(lifecycleService, this);

            new SanctuaryEffectTask(
                repository,
                territoryPresenceService,
                effectService,
                this::getMaximumTerritoryRadius,
                getLogger()
            ).start(this);
            getServer().getPluginManager().registerEvents(
                new ElytraSuppressionListener(
                    repository,
                    territoryPresenceService,
                    effectService,
                    this::getMaximumTerritoryRadius,
                    getLogger()
                ),
                this
            );

            extendedUi = new ExtendedUI(this);
            SentryUiService sentryUiService = new SentryUiService(
                this, extendedUi, repository, sentryRepository, sentryService, getLogger()
            );
            SanctuaryUiService uiService = new SanctuaryUiService(
                this,
                extendedUi,
                repository,
                permissionService,
                securityService,
                effectService,
                boundaryService,
                sentryUiService
            );
            getServer().getPluginManager().registerEvents(
                new SentryListener(
                    sentryService, sentryRepository, repository, anchorItemService, sentryUiService, getLogger()
                ),
                this
            );
            new SentryTask(sentryService, sentryRepository, repository, getLogger()).start(this);
            getServer().getPluginManager().registerEvents(
                new SanctuaryUiListener(
                    anchorItemService,
                    repository,
                    uiService,
                    getLogger()
                ),
                this
            );

            SanctuaryCommand sanctuaryCommand = new SanctuaryCommand(
                this,
                anchorItemService,
                lifecycleService,
                new DebugBeaconRegistrationService(repository),
                boundaryService,
                repository,
                permissionService,
                securityService,
                uiService
            );
            var command = Objects.requireNonNull(
                getCommand("sanctuary"),
                "sanctuary command is missing from plugin.yml"
            );
            command.setExecutor(sanctuaryCommand);
            command.setTabCompleter(sanctuaryCommand);

            validateConfiguration();

            getLogger().info("Sanctuary database initialized at " + databasePath.toAbsolutePath());
            getLogger().info("Sanctuary Beacon lifecycle, territory, awareness, trust, security, layered Beacon effects, player protections, and management UI and sentry defenses loaded.");
        } catch (SQLException | IOException | IllegalStateException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize Sanctuary", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (extendedUi != null) {
            extendedUi.close();
            extendedUi = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        sanctuaryApi = null;
    }

    public SanctuaryApi getSanctuaryApi() {
        if (sanctuaryApi == null) {
            throw new IllegalStateException("Sanctuary API is not available");
        }
        return sanctuaryApi;
    }

    public void reloadSanctuaryConfig() {
        reloadConfig();
        validateConfiguration();
    }

    public boolean isAnchorRecoveryEnabled() {
        return getConfig().getBoolean("anchors.recovery.enabled", true);
    }

    public long getAnchorRecoveryCooldownSeconds() {
        return Math.max(0L, getConfig().getLong("anchors.recovery.cooldown-seconds", 300L));
    }

    public int getMaximumTerritoryRadius() {
        return Math.max(16, getConfig().getInt("territory.maximum-radius", 128));
    }

    private void validateConfiguration() {
        if (getMaximumTerritoryRadius() < 16) {
            throw new IllegalStateException("territory.maximum-radius must be at least 16");
        }
    }
}

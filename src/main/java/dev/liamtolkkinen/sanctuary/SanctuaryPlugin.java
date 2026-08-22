package dev.liamtolkkinen.sanctuary;

import dev.liamtolkkinen.sanctuary.anchor.AnchorBreakListener;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemRemovalListener;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorLifecycleService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorPlacementListener;
import dev.liamtolkkinen.sanctuary.anchor.DebugBeaconRegistrationService;
import dev.liamtolkkinen.sanctuary.anchor.InitialAnchorPlacementService;
import dev.liamtolkkinen.sanctuary.api.DefaultSanctuaryApi;
import dev.liamtolkkinen.sanctuary.api.SanctuaryApi;
import dev.liamtolkkinen.sanctuary.command.SanctuaryCommand;
import dev.liamtolkkinen.sanctuary.persistence.DatabaseManager;
import dev.liamtolkkinen.sanctuary.persistence.MigrationRunner;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryTrustRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import dev.liamtolkkinen.sanctuary.territory.TerritoryAwarenessListener;
import dev.liamtolkkinen.sanctuary.territory.TerritoryBoundaryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryBoundaryProximityTask;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import java.util.logging.Level;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctuaryPlugin extends JavaPlugin {
    private static final double DEFAULT_INITIAL_TERRITORY_RADIUS = 18.0;
    private static final long DEFAULT_RECOVERY_COOLDOWN_SECONDS = 300L;
    private static final double DEFAULT_MAXIMUM_TERRITORY_RADIUS = 96.0;
    private static final double DEFAULT_TERRITORY_SPACING_MARGIN = 16.0;
    private static final double DEFAULT_BOUNDARY_PARTICLE_SPACING = 1.5;
    private static final int DEFAULT_BOUNDARY_DISPLAY_SECONDS = 10;
    private static final double DEFAULT_BOUNDARY_MAX_RENDER_DISTANCE = 128.0;
    private static final double DEFAULT_BOUNDARY_TRIGGER_DISTANCE = 12.0;
    private static final double DEFAULT_BOUNDARY_VERTICAL_SPACING = 1.5;
    private static final long DEFAULT_BOUNDARY_UPDATE_PERIOD_TICKS = 10L;

    private SanctuaryApi sanctuaryApi;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Path dataDirectory = getDataFolder().toPath();
            Files.createDirectories(dataDirectory);

            String databaseFilename = getConfig().getString("database.filename", "sanctuary.db");
            Path databasePath = dataDirectory.resolve(databaseFilename);
            DatabaseManager databaseManager = new DatabaseManager(databasePath);
            new MigrationRunner(databaseManager).migrate();

            var repository = new SqliteSanctuaryRepository(databaseManager);
            var trustRepository = new SqliteSanctuaryTrustRepository(databaseManager);
            var permissionService = new SanctuaryPermissionService(trustRepository);
            sanctuaryApi = new DefaultSanctuaryApi(repository, getLogger());

            getServer().getServicesManager().register(
                SanctuaryApi.class,
                sanctuaryApi,
                this,
                ServicePriority.Normal
            );

            AnchorItemService anchorItemService = new AnchorItemService(this);
            InitialAnchorPlacementService initialPlacementService =
                new InitialAnchorPlacementService(repository);
            AnchorLifecycleService lifecycleService = new AnchorLifecycleService(repository);

            getServer().getPluginManager().registerEvents(
                new AnchorPlacementListener(
                    anchorItemService,
                    initialPlacementService,
                    lifecycleService,
                    this::getInitialTerritoryRadius,
                    this::getMaximumTerritoryRadius,
                    this::getTerritorySpacingMargin,
                    getLogger()
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new AnchorBreakListener(
                    anchorItemService,
                    lifecycleService,
                    getLogger()
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new AnchorItemRemovalListener(
                    anchorItemService,
                    lifecycleService,
                    getLogger()
                ),
                this
            );
            TerritoryBoundaryService boundaryService = new TerritoryBoundaryService(this);

            getServer().getPluginManager().registerEvents(
                new TerritoryAwarenessListener(
                    repository,
                    new TerritoryPresenceService(),
                    this::isTerritoryEntryTitleEnabled,
                    this::isTerritoryExitMessageEnabled,
                    this::isOwnerEntryAlertsEnabled,
                    getLogger()
                ),
                this
            );
            new TerritoryBoundaryProximityTask(
                repository,
                boundaryService,
                this::isAutomaticBoundaryEnabled,
                this::getAutomaticBoundaryTriggerDistance,
                this::getBoundaryParticleSpacing,
                this::getBoundaryVerticalParticleSpacing,
                this::getAutomaticBoundaryUpdatePeriodTicks,
                getLogger()
            ).start(this);

            SanctuaryCommand sanctuaryCommand = new SanctuaryCommand(
                this,
                anchorItemService,
                lifecycleService,
                new DebugBeaconRegistrationService(repository),
                boundaryService,
                repository,
                permissionService
            );
            var command = Objects.requireNonNull(
                getCommand("sanctuary"),
                "sanctuary command is missing from plugin.yml"
            );
            command.setExecutor(sanctuaryCommand);
            command.setTabCompleter(sanctuaryCommand);

            validateConfiguration();

            getLogger().info("Sanctuary database initialized at " + databasePath.toAbsolutePath());
            getLogger().info("Sanctuary Beacon lifecycle, territory, awareness, trust, and capability support loaded.");
        } catch (SQLException | IOException | IllegalStateException exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize Sanctuary", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
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
        long value = getConfig().getLong(
            "anchors.recovery.cooldown-seconds",
            DEFAULT_RECOVERY_COOLDOWN_SECONDS
        );
        if (value < 0L) {
            throw new IllegalStateException(
                "anchors.recovery.cooldown-seconds must be zero or greater"
            );
        }
        return value;
    }

    private void validateConfiguration() {
        double initialRadius = getInitialTerritoryRadius();
        getAnchorRecoveryCooldownSeconds();
        if (getMaximumTerritoryRadius() < initialRadius) {
            throw new IllegalStateException(
                "territory.maximum-radius must be at least the initial territory radius ("
                    + initialRadius
                    + ")"
            );
        }
        getTerritorySpacingMargin();
        getBoundaryParticleSpacing();
        getBoundaryDisplaySeconds();
        getBoundaryMaximumRenderDistance();
        getAutomaticBoundaryTriggerDistance();
        getBoundaryVerticalParticleSpacing();
        getAutomaticBoundaryUpdatePeriodTicks();
    }

    public boolean isTerritoryEntryTitleEnabled() {
        return getConfig().getBoolean("territory.awareness.entry-title", true);
    }

    public boolean isTerritoryExitMessageEnabled() {
        return getConfig().getBoolean("territory.awareness.exit-message", false);
    }

    public boolean isOwnerEntryAlertsEnabled() {
        return getConfig().getBoolean("territory.awareness.owner-entry-alerts", true);
    }

    public double getBoundaryParticleSpacing() {
        double value = getConfig().getDouble(
            "territory.boundary.particle-spacing",
            DEFAULT_BOUNDARY_PARTICLE_SPACING
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "territory.boundary.particle-spacing must be finite and greater than zero"
            );
        }
        return value;
    }

    public int getBoundaryDisplaySeconds() {
        int value = getConfig().getInt(
            "territory.boundary.display-seconds",
            DEFAULT_BOUNDARY_DISPLAY_SECONDS
        );
        if (value < 1) {
            throw new IllegalStateException(
                "territory.boundary.display-seconds must be at least 1"
            );
        }
        return value;
    }

    public double getMaximumTerritoryRadius() {
        double value = getConfig().getDouble(
            "territory.maximum-radius",
            DEFAULT_MAXIMUM_TERRITORY_RADIUS
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "territory.maximum-radius must be finite and greater than zero"
            );
        }
        return value;
    }

    public double getTerritorySpacingMargin() {
        double value = getConfig().getDouble(
            "territory.spacing-margin",
            DEFAULT_TERRITORY_SPACING_MARGIN
        );
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalStateException(
                "territory.spacing-margin must be finite and zero or greater"
            );
        }
        return value;
    }

    public double getInitialTerritoryRadius() {
        double value = getConfig().getDouble(
            "anchors.initial-territory-radius",
            DEFAULT_INITIAL_TERRITORY_RADIUS
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "anchors.initial-territory-radius must be finite and greater than zero"
            );
        }
        return value;
    }

    public double getBoundaryMaximumRenderDistance() {
        double value = getConfig().getDouble(
            "territory.boundary.maximum-render-distance",
            DEFAULT_BOUNDARY_MAX_RENDER_DISTANCE
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "territory.boundary.maximum-render-distance must be finite and greater than zero"
            );
        }
        return value;
    }

    public boolean isAutomaticBoundaryEnabled() {
        return getConfig().getBoolean("territory.boundary.automatic.enabled", true);
    }

    public double getAutomaticBoundaryTriggerDistance() {
        double value = getConfig().getDouble(
            "territory.boundary.automatic.trigger-distance",
            DEFAULT_BOUNDARY_TRIGGER_DISTANCE
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "territory.boundary.automatic.trigger-distance must be finite and greater than zero"
            );
        }
        return value;
    }

    public long getAutomaticBoundaryUpdatePeriodTicks() {
        long value = getConfig().getLong(
            "territory.boundary.automatic.update-period-ticks",
            DEFAULT_BOUNDARY_UPDATE_PERIOD_TICKS
        );
        if (value < 1L) {
            throw new IllegalStateException(
                "territory.boundary.automatic.update-period-ticks must be at least 1"
            );
        }
        return value;
    }

    public double getBoundaryVerticalParticleSpacing() {
        double value = getConfig().getDouble(
            "territory.boundary.automatic.vertical-particle-spacing",
            DEFAULT_BOUNDARY_VERTICAL_SPACING
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "territory.boundary.automatic.vertical-particle-spacing must be finite and greater than zero"
            );
        }
        return value;
    }
}

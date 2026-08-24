package dev.liamtolkkinen.sanctuary;

import dev.liamtolkkinen.extendedui.ExtendedUI;
import dev.liamtolkkinen.sanctuary.anchor.AnchorBeamTask;
import dev.liamtolkkinen.sanctuary.anchor.AnchorBreakListener;
import dev.liamtolkkinen.sanctuary.anchor.AnchorGraphService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemRemovalListener;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorLifecycleService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorPlacementListener;
import dev.liamtolkkinen.sanctuary.anchor.DebugBeaconRegistrationService;
import dev.liamtolkkinen.sanctuary.api.DefaultSanctuaryApi;
import dev.liamtolkkinen.sanctuary.api.SanctuaryApi;
import dev.liamtolkkinen.sanctuary.command.SanctuaryCommand;
import dev.liamtolkkinen.sanctuary.companion.CompanionRuntime;
import dev.liamtolkkinen.sanctuary.effect.ElytraSuppressionListener;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectService;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectTask;
import dev.liamtolkkinen.sanctuary.persistence.DatabaseManager;
import dev.liamtolkkinen.sanctuary.persistence.MigrationRunner;
import dev.liamtolkkinen.sanctuary.persistence.SqliteAnchorEffectRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryAnchorRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryEffectRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuarySecurityRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryTrustRepository;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSentryRepository;
import dev.liamtolkkinen.sanctuary.protection.SanctuaryProtectionListener;
import dev.liamtolkkinen.sanctuary.protection.SanctuaryProtectionService;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.sentry.SentryListener;
import dev.liamtolkkinen.sanctuary.sentry.SentryRecipeService;
import dev.liamtolkkinen.sanctuary.sentry.SentryService;
import dev.liamtolkkinen.sanctuary.sentry.SentryTask;
import dev.liamtolkkinen.sanctuary.sentry.SentryUiService;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryAwarenessListener;
import dev.liamtolkkinen.sanctuary.territory.TerritoryBoundaryProximityTask;
import dev.liamtolkkinen.sanctuary.territory.TerritoryBoundaryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryPresenceService;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryPermissionService;
import dev.liamtolkkinen.sanctuary.ui.SanctuaryUiListener;
import dev.liamtolkkinen.sanctuary.ui.SanctuaryUiService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Particle;
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
    private static final double DEFAULT_BOUNDARY_MINIMUM_DISTANCE = 3.0;
    private static final double DEFAULT_BOUNDARY_MAXIMUM_DISTANCE = 12.0;
    private static final String DEFAULT_BOUNDARY_OWNER_PARTICLE = "SCULK_CHARGE_POP";
    private static final String DEFAULT_BOUNDARY_TRUSTED_PARTICLE = "GLOW";
    private static final String DEFAULT_BOUNDARY_NEUTRAL_PARTICLE = "END_ROD";
    private static final String DEFAULT_BOUNDARY_HOSTILE_PARTICLE = "REVERSE_PORTAL";
    private static final double DEFAULT_BOUNDARY_VERTICAL_SPACING = 1.5;
    private static final long DEFAULT_BOUNDARY_UPDATE_PERIOD_TICKS = 10L;

    private SanctuaryApi sanctuaryApi;
    private ExtendedUI extendedUi;

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
            var anchorRepository = new SqliteSanctuaryAnchorRepository(databaseManager);
            var trustRepository = new SqliteSanctuaryTrustRepository(databaseManager);
            var permissionService = new SanctuaryPermissionService(trustRepository);
            var securityRepository = new SqliteSanctuarySecurityRepository(databaseManager);
            var securityService = new SanctuarySecurityService(securityRepository, permissionService);
            var effectRepository = new SqliteSanctuaryEffectRepository(databaseManager);
            var anchorEffectRepository = new SqliteAnchorEffectRepository(databaseManager);
            var effectService = new SanctuaryEffectService(
                effectRepository,
                anchorEffectRepository,
                securityService
            );
            var sentryRepository = new SqliteSentryRepository(databaseManager);
            var graphService = new AnchorGraphService(repository, anchorRepository);
            var anchorTerritoryService = new AnchorTerritoryService(repository, anchorRepository);
            sanctuaryApi = new DefaultSanctuaryApi(repository, getLogger());

            getServer().getServicesManager().register(
                SanctuaryApi.class,
                sanctuaryApi,
                this,
                ServicePriority.Normal
            );

            AnchorItemService anchorItemService = new AnchorItemService(this);
            AnchorLifecycleService lifecycleService = new AnchorLifecycleService(repository);

            getServer().getPluginManager().registerEvents(
                new AnchorPlacementListener(
                    anchorItemService,
                    graphService,
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
                    graphService,
                    anchorRepository,
                    getLogger()
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new AnchorItemRemovalListener(
                    anchorItemService,
                    graphService,
                    getLogger()
                ),
                this
            );
            TerritoryBoundaryService boundaryService = new TerritoryBoundaryService(
                this,
                securityService,
                this::getBoundaryOwnerParticle,
                this::getBoundaryTrustedParticle,
                this::getBoundaryNeutralParticle,
                this::getBoundaryHostileParticle,
                getLogger()
            );

            TerritoryPresenceService territoryPresenceService = new TerritoryPresenceService();
            SentryService sentryService = new SentryService(
                this, repository, sentryRepository, securityService, territoryPresenceService, getLogger()
            );
            new SentryRecipeService(this).registerAll();
            getServer().getPluginManager().registerEvents(
                new TerritoryAwarenessListener(
                    repository,
                    territoryPresenceService,
                    securityService,
                    this::isTerritoryEntryTitleEnabled,
                    this::isTerritoryExitMessageEnabled,
                    this::isOwnerEntryAlertsEnabled,
                    getLogger()
                ),
                this
            );
            getServer().getPluginManager().registerEvents(
                new SanctuaryProtectionListener(
                    new SanctuaryProtectionService(anchorTerritoryService, permissionService),
                    this::isHardProtectionEnabled,
                    getLogger()
                ),
                this
            );
            new TerritoryBoundaryProximityTask(
                repository,
                boundaryService,
                this::isAutomaticBoundaryEnabled,
                this::getAutomaticBoundaryMinimumDistance,
                this::getAutomaticBoundaryMaximumDistance,
                this::getBoundaryParticleSpacing,
                this::getBoundaryVerticalParticleSpacing,
                this::getAutomaticBoundaryUpdatePeriodTicks,
                getLogger()
            ).start(this);
            new SanctuaryEffectTask(
                repository,
                anchorTerritoryService,
                effectService,
                this::getMaximumTerritoryRadius,
                getLogger()
            ).start(this);
            new AnchorBeamTask(
                repository,
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
            CompanionRuntime.start(this, extendedUi);
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
            getLogger().info("Sanctuary anchor graph, Beacon and Conduit effects, territory, trust, security, player protections, management UI, companions, and sentry defenses loaded.");
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
        getAutomaticBoundaryMinimumDistance();
        getAutomaticBoundaryMaximumDistance();
        getBoundaryOwnerParticle();
        getBoundaryTrustedParticle();
        getBoundaryNeutralParticle();
        getBoundaryHostileParticle();
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

    public double getAutomaticBoundaryMinimumDistance() {
        double value = getConfig().getDouble(
            "territory.boundary.automatic.minimum-distance",
            DEFAULT_BOUNDARY_MINIMUM_DISTANCE
        );
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalStateException(
                "territory.boundary.automatic.minimum-distance must be finite and zero or greater"
            );
        }
        return value;
    }

    public double getAutomaticBoundaryMaximumDistance() {
        double fallback = getConfig().getDouble(
            "territory.boundary.automatic.trigger-distance",
            DEFAULT_BOUNDARY_MAXIMUM_DISTANCE
        );
        double value = getConfig().getDouble(
            "territory.boundary.automatic.maximum-distance",
            fallback
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "territory.boundary.automatic.maximum-distance must be finite and greater than zero"
            );
        }
        double minimum = getAutomaticBoundaryMinimumDistance();
        if (value <= minimum) {
            throw new IllegalStateException(
                "territory.boundary.automatic.maximum-distance must be greater than minimum-distance"
            );
        }
        return value;
    }

    public Particle getBoundaryOwnerParticle() {
        return getBoundaryParticle("owner", DEFAULT_BOUNDARY_OWNER_PARTICLE);
    }

    public Particle getBoundaryTrustedParticle() {
        return getBoundaryParticle("trusted", DEFAULT_BOUNDARY_TRUSTED_PARTICLE);
    }

    public Particle getBoundaryNeutralParticle() {
        return getBoundaryParticle("neutral", DEFAULT_BOUNDARY_NEUTRAL_PARTICLE);
    }

    public Particle getBoundaryHostileParticle() {
        return getBoundaryParticle("hostile", DEFAULT_BOUNDARY_HOSTILE_PARTICLE);
    }

    private Particle getBoundaryParticle(String relationship, String defaultName) {
        String key = "territory.boundary.particles." + relationship;
        String configured = getConfig().getString(key, defaultName);
        if (configured == null || configured.isBlank()) {
            configured = defaultName;
        }
        try {
            Particle particle = Particle.valueOf(configured.trim().toUpperCase(java.util.Locale.ROOT));
            if (particle.getDataType() != Void.class) {
                getLogger().warning(
                    "Particle '" + configured + "' for " + key
                        + " requires extra particle data; using " + defaultName
                );
                return Particle.valueOf(defaultName);
            }
            return particle;
        } catch (IllegalArgumentException exception) {
            getLogger().warning(
                "Invalid particle '" + configured + "' for " + key + "; using " + defaultName
            );
            return Particle.valueOf(defaultName);
        }
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

    public boolean areHardProtectionsEnabled() {
        return getConfig().getBoolean("protections.hard.enabled", false);
    }

    public boolean isHardProtectionEnabled(SanctuaryCapability capability) {
        Objects.requireNonNull(capability, "capability");
        if (!areHardProtectionsEnabled()) {
            return false;
        }
        String key = switch (capability) {
            case BUILD -> "block-place";
            case BREAK -> "block-break";
            case INTERACT -> "interactions";
            case CONTAINER -> "containers";
            case REDSTONE -> "redstone";
            case ENTITIES -> "entities";
        };
        return getConfig().getBoolean("protections.hard." + key, true);
    }
}

package dev.liamtolkkinen.sanctuary;

import dev.liamtolkkinen.sanctuary.anchor.AnchorBreakListener;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemRemovalListener;
import dev.liamtolkkinen.sanctuary.anchor.AnchorItemService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorLifecycleService;
import dev.liamtolkkinen.sanctuary.anchor.AnchorPlacementListener;
import dev.liamtolkkinen.sanctuary.anchor.InitialAnchorPlacementService;
import dev.liamtolkkinen.sanctuary.api.DefaultSanctuaryApi;
import dev.liamtolkkinen.sanctuary.api.SanctuaryApi;
import dev.liamtolkkinen.sanctuary.command.SanctuaryCommand;
import dev.liamtolkkinen.sanctuary.persistence.DatabaseManager;
import dev.liamtolkkinen.sanctuary.persistence.MigrationRunner;
import dev.liamtolkkinen.sanctuary.persistence.SqliteSanctuaryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctuaryPlugin extends JavaPlugin {
    private static final double DEFAULT_INITIAL_TERRITORY_AREA = 100.0;
    private static final long DEFAULT_RECOVERY_COOLDOWN_SECONDS = 300L;

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
                    this::getInitialTerritoryArea,
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

            SanctuaryCommand sanctuaryCommand = new SanctuaryCommand(
                this,
                anchorItemService,
                lifecycleService,
                repository
            );
            var command = Objects.requireNonNull(
                getCommand("sanctuary"),
                "sanctuary command is missing from plugin.yml"
            );
            command.setExecutor(sanctuaryCommand);
            command.setTabCompleter(sanctuaryCommand);

            validateConfiguration();

            getLogger().info("Sanctuary database initialized at " + databasePath.toAbsolutePath());
            getLogger().info("Sanctuary complete Beacon anchor lifecycle support loaded.");
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
        getInitialTerritoryArea();
        getAnchorRecoveryCooldownSeconds();
    }

    private double getInitialTerritoryArea() {
        double value = getConfig().getDouble(
            "anchors.initial-territory-area",
            DEFAULT_INITIAL_TERRITORY_AREA
        );
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalStateException(
                "anchors.initial-territory-area must be finite and greater than zero"
            );
        }
        return value;
    }
}

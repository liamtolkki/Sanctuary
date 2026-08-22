package dev.liamtolkkinen.sanctuary;

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

            SanctuaryCommand sanctuaryCommand = new SanctuaryCommand(this);
            var command = Objects.requireNonNull(
                getCommand("sanctuary"),
                "sanctuary command is missing from plugin.yml"
            );
            command.setExecutor(sanctuaryCommand);
            command.setTabCompleter(sanctuaryCommand);

            getLogger().info("Sanctuary database initialized at " + databasePath.toAbsolutePath());
            getLogger().info("Sanctuary foundation loaded successfully.");
        } catch (SQLException | IOException exception) {
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
    }
}

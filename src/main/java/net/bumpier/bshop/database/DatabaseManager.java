package net.bumpier.bshop.database;

import net.bumpier.bshop.BShop;

import java.util.logging.Level;

public class DatabaseManager {

    private final BShop plugin;
    private Database database;

    public DatabaseManager(BShop plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        String dbType = plugin.getConfig().getString("database.type", "SQLite").toUpperCase();
        switch (dbType) {
            case "MYSQL":
                this.database = new MySQLDatabase(plugin);
                break;
            case "SQLITE":
            default:
                this.database = new SQLiteDatabase(plugin);
                break;
        }

        plugin.getLogger().info("Using " + dbType + " for database storage.");
        this.database.connect();
        this.database.createTables();
    }

    public void shutdown() {
        if (this.database != null) {
            this.database.disconnect();
        }
    }

    public Database getDatabase() {
        return this.database;
    }
}
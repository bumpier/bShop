package net.bumpier.bshop.database;

import net.bumpier.bshop.BShop;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteDatabase implements Database {

    private final BShop plugin;
    private Connection connection;
    private final File dbFile;

    public SQLiteDatabase(BShop plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "database.db");
    }

    @Override
    public void connect() {
        if (!dbFile.exists()) {
            try {
                dbFile.getParentFile().mkdirs();
                dbFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite database file.", e);
                return;
            }
        }
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("SQLite connection established.");
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database.", e);
        }
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("SQLite connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error while closing SQLite connection.", e);
            }
        }
    }

    @Override
    public CompletableFuture<Connection> getConnection() {
        return CompletableFuture.completedFuture(connection);
    }

    @Override
    public void createTables() {
        // Run table creation asynchronously to not block the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection().join();
                 Statement statement = conn.createStatement()) {

                // Example Table: A table to store shop items.
                String createItemsTable = "CREATE TABLE IF NOT EXISTS bshop_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "shop_id VARCHAR(64) NOT NULL," +
                        "item_id VARCHAR(128) NOT NULL," +
                        "material VARCHAR(64) NOT NULL," +
                        "buy_price DOUBLE DEFAULT -1," +
                        "sell_price DOUBLE DEFAULT -1," +
                        "stock INTEGER DEFAULT -1," +
                        "UNIQUE(shop_id, item_id));";

                statement.execute(createItemsTable);

                plugin.getLogger().info("Database tables initialized successfully.");

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create database tables.", e);
            }
        });
    }
}
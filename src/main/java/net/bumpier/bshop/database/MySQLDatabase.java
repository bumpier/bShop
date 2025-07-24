package net.bumpier.bshop.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.bumpier.bshop.BShop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLDatabase implements Database {

    private final BShop plugin;
    private HikariDataSource dataSource;

    public MySQLDatabase(BShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database.mysql");
        if (dbConfig == null) {
            plugin.getLogger().severe("MySQL configuration section is missing from config.yml!");
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + dbConfig.getString("host") + ":" + dbConfig.getInt("port") + "/" + dbConfig.getString("database"));
        config.setUsername(dbConfig.getString("username"));
        config.setPassword(dbConfig.getString("password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(config);
        plugin.getLogger().info("MySQL connection pool established.");
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }

    @Override
    public CompletableFuture<Connection> getConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to retrieve a connection from the pool.", e);
                return null;
            }
        });
    }

    @Override
    public void createTables() {
        // Run table creation asynchronously.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection().join();
                 Statement statement = conn.createStatement()) {

                // Same table schema, but with MySQL syntax.
                String createItemsTable = "CREATE TABLE IF NOT EXISTS `bshop_items` (" +
                        "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                        "`shop_id` VARCHAR(64) NOT NULL," +
                        "`item_id` VARCHAR(128) NOT NULL," +
                        "`material` VARCHAR(64) NOT NULL," +
                        "`buy_price` DOUBLE DEFAULT -1," +
                        "`sell_price` DOUBLE DEFAULT -1," +
                        "`stock` INT DEFAULT -1," +
                        "UNIQUE KEY `shop_item_idx` (`shop_id`, `item_id`));";

                statement.execute(createItemsTable);
                plugin.getLogger().info("Database tables initialized successfully.");

            } catch (Exception e) { // Catch broader exceptions for CompletableFuture
                plugin.getLogger().log(Level.SEVERE, "Failed to create database tables.", e);
            }
        });
    }
}
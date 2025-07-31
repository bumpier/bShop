package net.bumpier.bshop.database;

import net.bumpier.bshop.BShop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class SQLiteDatabase implements Database {

    private final BShop plugin;
    private final File dbFile;
    private final ConcurrentLinkedQueue<Connection> connectionPool;
    private final AtomicInteger activeConnections;
    private final int maxPoolSize;
    private final int minPoolSize;
    private volatile boolean isShutdown = false;

    public SQLiteDatabase(BShop plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "database.db");
        
        // Load pool settings from config
        ConfigurationSection perfConfig = plugin.getConfig().getConfigurationSection("performance.database");
        this.maxPoolSize = perfConfig != null ? perfConfig.getInt("connection_pool_size", 10) : 10;
        this.minPoolSize = perfConfig != null ? perfConfig.getInt("minimum_idle", 2) : 2;
        
        this.connectionPool = new ConcurrentLinkedQueue<>();
        this.activeConnections = new AtomicInteger(0);
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
            
            // Initialize connection pool
            for (int i = 0; i < minPoolSize; i++) {
                Connection conn = createConnection();
                if (conn != null) {
                    connectionPool.offer(conn);
                }
            }
            

        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found.", e);
        }
    }

    private Connection createConnection() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            
            // Enable WAL mode for better concurrent performance
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA mmap_size=268435456"); // 256MB
                stmt.execute("PRAGMA optimize");
            }
            
            return conn;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create SQLite connection.", e);
            return null;
        }
    }

    @Override
    public void disconnect() {
        isShutdown = true;
        
        // Close all connections in the pool
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection.", e);
            }
        }
        
        plugin.getLogger().info("SQLite connection pool closed. Active connections: " + activeConnections.get());
    }

    @Override
    public CompletableFuture<Connection> getConnection() {
        return CompletableFuture.supplyAsync(() -> {
            if (isShutdown) {
                throw new RuntimeException("Database is shutdown");
            }
            
            // Try to get connection from pool
            Connection conn = connectionPool.poll();
            if (conn != null) {
                try {
                    // Test if connection is still valid
                    if (conn.isValid(1)) {
                        activeConnections.incrementAndGet();
                        return conn;
                    } else {
                        conn.close();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid connection in pool, creating new one.", e);
                }
            }
            
            // Create new connection if pool is not full
            if (activeConnections.get() < maxPoolSize) {
                conn = createConnection();
                if (conn != null) {
                    activeConnections.incrementAndGet();
                    return conn;
                }
            }
            
            // Wait for a connection to become available
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 5000) { // 5 second timeout
                conn = connectionPool.poll();
                if (conn != null) {
                    try {
                        if (conn.isValid(1)) {
                            activeConnections.incrementAndGet();
                            return conn;
                        } else {
                            conn.close();
                        }
                    } catch (SQLException e) {
                        // Continue waiting
                    }
                }
                
                try {
                    Thread.sleep(10); // Small delay to prevent busy waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            throw new RuntimeException("Could not obtain database connection within timeout");
        });
    }

    public void returnConnection(Connection connection) {
        if (connection == null || isShutdown) {
            return;
        }
        
        try {
            // Reset connection state
            connection.setAutoCommit(true);
            connection.clearWarnings();
            
            // Return to pool if not full
            if (connectionPool.size() < maxPoolSize) {
                connectionPool.offer(connection);
            } else {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error returning connection to pool.", e);
            try {
                connection.close();
            } catch (SQLException ex) {
                // Ignore
            }
        } finally {
            activeConnections.decrementAndGet();
        }
    }

    @Override
    public void createTables() {
        // Run table creation asynchronously to not block the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection().join()) {
                try (Statement statement = conn.createStatement()) {
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

                    // Create indexes for better performance
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_bshop_items_shop_id ON bshop_items(shop_id)");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_bshop_items_item_id ON bshop_items(item_id)");

                    plugin.getLogger().info("Database tables initialized successfully.");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create database tables.", e);
            }
        });
    }
}
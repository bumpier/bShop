package net.bumpier.bshop;

import org.bukkit.plugin.java.JavaPlugin;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.util.message.MessageService;
import net.bumpier.bshop.util.config.ConfigManager;
import net.bumpier.bshop.module.ModuleManager;
import net.bumpier.bshop.shop.ShopModule;
import net.bumpier.bshop.util.MultiplierService;
import net.bumpier.bshop.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.ConfigurationSection;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class BShop extends JavaPlugin {
    private static BShop instance;
    private ShopManager shopManager;
    private ShopGuiManager shopGuiManager;
    private ShopTransactionService transactionService;
    private MessageService messageService;
    private ModuleManager moduleManager;
    private MultiplierService multiplierService;
    private DatabaseManager databaseManager;
    private Economy economy;
    private BukkitAudiences adventure;
    
    // Performance monitoring
    private final AtomicLong startupTime = new AtomicLong(0);
    private final AtomicLong shutdownTime = new AtomicLong(0);
    private volatile boolean isShuttingDown = false;

    public static BShop getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        this.startupTime.set(startTime);
        
        try {
            instance = this;
            saveDefaultConfig();
            

            
            // Initialize Vault Economy first
            if (!initializeEconomy()) {
                getLogger().warning("Economy initialization failed, some features may be limited.");
            }
            
            // Initialize Adventure
            adventure = BukkitAudiences.create(this);
            
            // Initialize core services in order
            if (!initializeCoreServices()) {
                getLogger().severe("Core services initialization failed!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize shop services
            if (!initializeShopServices()) {
                getLogger().severe("Shop services initialization failed!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize modules
            if (!initializeModules()) {
                getLogger().warning("Module initialization had issues, but continuing...");
            }
            
            // Start performance monitoring
            startPerformanceMonitoring();
            
            long totalTime = System.currentTimeMillis() - startTime;
            getLogger().info("bShop enabled successfully in " + totalTime + "ms!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin initialization", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private boolean initializeEconomy() {
        try {
            if (getServer().getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();

                    return true;
                } else {
                    getLogger().warning("Vault found but no economy provider registered!");
                    return false;
                }
            } else {
                getLogger().warning("Vault not found! Economy features will be disabled.");
                return false;
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error initializing economy", e);
            return false;
        }
    }
    
    private boolean initializeCoreServices() {
        try {
            // Initialize configuration managers
            ConfigManager messagesConfig = new ConfigManager(this, "messages.yml");
            ConfigManager guisConfig = new ConfigManager(this, "guis.yml");
            
            // Initialize message service
            messageService = new MessageService(this, messagesConfig);
            
            // Initialize multiplier service
            multiplierService = new MultiplierService(this);
            
            // Initialize database manager
            databaseManager = new DatabaseManager(this);
            

            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing core services", e);
            return false;
        }
    }
    
    private boolean initializeShopServices() {
        try {
            // Initialize shop manager
            shopManager = new ShopManager(this);
            shopManager.startRotationTask(this);
            
            // Initialize GUI manager
            ConfigManager guisConfig = new ConfigManager(this, "guis.yml");
            shopGuiManager = new ShopGuiManager(this, shopManager, messageService, guisConfig);
            
            // Initialize transaction service
            transactionService = new ShopTransactionService(this, messageService, shopGuiManager);
            

            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing shop services", e);
            return false;
        }
    }
    
    private boolean initializeModules() {
        try {
            // Initialize modules
            moduleManager = new ModuleManager(this);
            moduleManager.loadModules();
            ShopModule shopModule = new ShopModule(this, moduleManager);
            shopModule.onEnable();
            

            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error initializing modules", e);
            return false;
        }
    }
    
    private void startPerformanceMonitoring() {
        // Check if monitoring is enabled
        ConfigurationSection monitoringConfig = getConfig().getConfigurationSection("performance.monitoring");
        boolean monitoringEnabled = monitoringConfig != null ? monitoringConfig.getBoolean("enabled", true) : true;
        int logIntervalMinutes = monitoringConfig != null ? monitoringConfig.getInt("log_interval_minutes", 10) : 10;
        boolean enableDetailedStats = monitoringConfig != null ? monitoringConfig.getBoolean("enable_detailed_stats", false) : false;
        long performanceAlertThreshold = monitoringConfig != null ? monitoringConfig.getLong("performance_alert_threshold_ms", 1000) : 1000;
        
        if (!monitoringEnabled) {
            getLogger().info("Performance monitoring is disabled in configuration.");
            return;
        }
        
        // Start periodic performance monitoring
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isShuttingDown) {
                    this.cancel();
                    return;
                }
                
                try {
                    // Log performance statistics at configured interval
                    if (System.currentTimeMillis() % (logIntervalMinutes * 60000) < 5000) { // Every configured minutes
                        logPerformanceStats(enableDetailedStats, performanceAlertThreshold);
                    }
                    
                    // Clean up caches periodically
                    if (shopGuiManager != null) {
                        shopGuiManager.cleanupItemStackCache();
                    }
                    
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Error during performance monitoring", e);
                }
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L); // Every 5 minutes
    }
    
    private void logPerformanceStats(boolean enableDetailedStats, long performanceAlertThreshold) {
        try {
            getLogger().info("=== Performance Statistics ===");
            
            if (shopManager != null) {
                var shopStats = shopManager.getCacheStats();
                long cachedShops = (Long) shopStats.get("cached_shops");
                double hitRate = (Double) shopStats.get("hit_rate_percent");
                getLogger().info("Shop Cache: " + cachedShops + " shops, " + String.format("%.1f%%", hitRate) + " hit rate");
                
                // Performance alert for low cache hit rate
                if (hitRate < 50.0 && enableDetailedStats) {
                    getLogger().warning("Low shop cache hit rate detected: " + String.format("%.1f%%", hitRate));
                }
            }
            
            if (transactionService != null) {
                var txStats = transactionService.getTransactionStats();
                long totalTx = (Long) txStats.get("total_transactions");
                double successRate = (Double) txStats.get("success_rate_percent");
                getLogger().info("Transactions: " + totalTx + " total, " + String.format("%.1f%%", successRate) + " success rate");
                
                // Performance alert for low success rate
                if (successRate < 90.0 && enableDetailedStats) {
                    getLogger().warning("Low transaction success rate detected: " + String.format("%.1f%%", successRate));
                }
            }
            
            if (multiplierService != null) {
                var multStats = multiplierService.getMultiplierStats();
                long activeMultipliers = (Long) multStats.get("active_temporary_multipliers");
                double cacheHitRate = (Double) multStats.get("cache_hit_rate_percent");
                getLogger().info("Multipliers: " + activeMultipliers + " active, " + String.format("%.1f%%", cacheHitRate) + " cache hit rate");
            }
            
            if (shopGuiManager != null) {
                var guiStats = shopGuiManager.getGuiStats();
                long openInventories = (Long) guiStats.get("open_shop_inventories");
                long cachedItemStacks = (Long) guiStats.get("item_stack_cache_size");
                getLogger().info("GUI: " + openInventories + " open inventories, " + cachedItemStacks + " cached item stacks");
            }
            
            // Memory usage monitoring
            if (enableDetailedStats) {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                long maxMemory = runtime.maxMemory();
                double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                getLogger().info("Memory Usage: " + String.format("%.1f%%", memoryUsagePercent) + " (" + 
                    (usedMemory / 1024 / 1024) + "MB / " + (maxMemory / 1024 / 1024) + "MB)");
                
                // Performance alert for high memory usage
                if (memoryUsagePercent > 80.0) {
                    getLogger().warning("High memory usage detected: " + String.format("%.1f%%", memoryUsagePercent));
                }
            }
            
            getLogger().info("================================");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error logging performance stats", e);
        }
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();
        this.shutdownTime.set(startTime);
        this.isShuttingDown = true;
        
        getLogger().info("Starting bShop shutdown...");
        
        try {
            // Shutdown services gracefully in reverse order
            if (adventure != null) {
                adventure.close();
            }
            
            // Shutdown transaction service
            if (transactionService != null) {
                transactionService.shutdown();
            }
            
            // Shutdown shop manager
            if (shopManager != null) {
                shopManager.shutdown();
            }
            
            // Shutdown modules
            if (moduleManager != null) {
                moduleManager.unloadModules();
            }
            
            // Shutdown database manager
            if (databaseManager != null) {
                databaseManager.shutdown();
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            getLogger().info("bShop disabled successfully in " + totalTime + "ms!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }
    
    /**
     * Get plugin performance statistics
     */
    public java.util.Map<String, Object> getPluginStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        stats.put("startup_time_ms", startupTime.get());
        stats.put("shutdown_time_ms", shutdownTime.get());
        stats.put("uptime_ms", System.currentTimeMillis() - startupTime.get());
        stats.put("is_shutting_down", isShuttingDown);
        
        // Add service-specific stats
        if (shopManager != null) {
            stats.put("shop_manager_stats", shopManager.getCacheStats());
        }
        if (transactionService != null) {
            stats.put("transaction_service_stats", transactionService.getTransactionStats());
        }
        if (multiplierService != null) {
            stats.put("multiplier_service_stats", multiplierService.getMultiplierStats());
        }
        if (shopGuiManager != null) {
            stats.put("gui_manager_stats", shopGuiManager.getGuiStats());
        }
        
        return stats;
    }

    // Getters for API access
    public ShopManager getShopManager() { return shopManager; }
    public ShopGuiManager getShopGuiManager() { return shopGuiManager; }
    public ShopTransactionService getTransactionService() { return transactionService; }
    public MessageService getMessageService() { return messageService; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public MultiplierService getMultiplierService() { return multiplierService; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public Economy getEconomy() { return economy; }
    public BukkitAudiences adventure() { return adventure; }
    public boolean isShuttingDown() { return isShuttingDown; }
    

} 
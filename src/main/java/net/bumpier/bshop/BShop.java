package net.bumpier.bshop;

import net.bumpier.bshop.database.DatabaseManager;
import net.bumpier.bshop.module.ModuleManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class BShop extends JavaPlugin {

    private static BShop instance;
    private ModuleManager moduleManager;
    private DatabaseManager databaseManager;
    private Economy economy;
    private BukkitAudiences adventure;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize Adventure platform for modern component support
        this.adventure = BukkitAudiences.create(this);

        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Disabled due to no Vault dependency found!");
            if (this.adventure != null) {
                this.adventure.close(); // Clean up adventure if initialization fails
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);
        this.moduleManager = new ModuleManager(this);
        moduleManager.loadModules();
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.unloadModules();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        // Gracefully close the Adventure platform instance to free up resources
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static BShop getInstance() {
        return instance;
    }

    /**
     * Provides the BukkitAudiences instance for sending Adventure components.
     *
     * @return The active BukkitAudiences instance.
     * @throws IllegalStateException if the plugin is disabled.
     */
    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Attempted to access Adventure when the plugin is disabled!");
        }
        return this.adventure;
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
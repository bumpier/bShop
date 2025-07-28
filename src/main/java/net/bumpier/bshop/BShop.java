package net.bumpier.bshop;

import org.bukkit.plugin.java.JavaPlugin;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.util.message.MessageService;
import net.bumpier.bshop.util.config.ConfigManager;
import net.bumpier.bshop.command.ShopCommand;
import net.bumpier.bshop.module.ModuleManager;
import net.bumpier.bshop.shop.ShopModule;
import net.bumpier.bshop.util.MultiplierService;
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.RegisteredServiceProvider;

public class BShop extends JavaPlugin {
    private static BShop instance;
    private ShopManager shopManager;
    private ShopGuiManager shopGuiManager;
    private ShopTransactionService transactionService;
    private MessageService messageService;
    private ModuleManager moduleManager;
    private MultiplierService multiplierService;
    private Economy economy;
    private BukkitAudiences adventure;

    public static BShop getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        // Initialize Vault Economy first
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().info("Vault Economy found: " + economy.getName());
            } else {
                getLogger().warning("Vault found but no economy provider registered!");
            }
        } else {
            getLogger().warning("Vault not found! Economy features will be disabled.");
        }
        
        // Initialize Adventure
        adventure = BukkitAudiences.create(this);
        
        // Initialize core services
        ConfigManager messagesConfig = new ConfigManager(this, "messages.yml");
        ConfigManager guisConfig = new ConfigManager(this, "guis.yml");
        messageService = new MessageService(this, messagesConfig);
        multiplierService = new MultiplierService(this);
        
        // Initialize shop services
        shopManager = new ShopManager(this);
        shopManager.startRotationTask(this);
        shopGuiManager = new ShopGuiManager(this, shopManager, messageService, guisConfig);
        transactionService = new ShopTransactionService(this, messageService, shopGuiManager);
        
        // Initialize modules
        moduleManager = new ModuleManager(this);
        moduleManager.loadModules();
        ShopModule shopModule = new ShopModule(this, moduleManager);
        shopModule.onEnable();
        
        getLogger().info("bShop enabled!");
    }

    @Override
    public void onDisable() {
        if (adventure != null) {
            adventure.close();
        }
        if (moduleManager != null) {
            moduleManager.unloadModules();
        }
        getLogger().info("bShop disabled!");
    }

    // Getters for API access
    public ShopManager getShopManager() { return shopManager; }
    public ShopGuiManager getShopGuiManager() { return shopGuiManager; }
    public ShopTransactionService getTransactionService() { return transactionService; }
    public MessageService getMessageService() { return messageService; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public MultiplierService getMultiplierService() { return multiplierService; }
    public Economy getEconomy() { return economy; }
    public BukkitAudiences adventure() { return adventure; }
} 
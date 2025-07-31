package net.bumpier.bshop.api;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.util.MultiplierService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Main API class for bShop plugin
 * Provides access to all major functionality
 */
public class BShopAPI {
    
    private static BShopAPI instance;
    private final BShop plugin;
    private final ShopManager shopManager;
    private final ShopGuiManager shopGuiManager;
    private final ShopTransactionService transactionService;
    private final MultiplierService multiplierService;
    private final MultiplierAPI multiplierAPI;
    
    private BShopAPI(BShop plugin) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
        this.shopGuiManager = plugin.getShopGuiManager();
        this.transactionService = plugin.getTransactionService();
        this.multiplierService = plugin.getMultiplierService();
        this.multiplierAPI = new MultiplierAPI(this);
    }
    
    /**
     * Get the API instance
     * @return BShopAPI instance
     */
    public static BShopAPI getInstance() {
        if (instance == null) {
            BShop plugin = BShop.getInstance();
            if (plugin == null) {
                throw new IllegalStateException("bShop plugin is not enabled!");
            }
            instance = new BShopAPI(plugin);
        }
        return instance;
    }
    
    /**
     * Check if the API is available
     * @return true if bShop is enabled and API is ready
     */
    public static boolean isAvailable() {
        return BShop.getInstance() != null;
    }
    
    // --- Shop Management ---
    
    /**
     * Get the shop manager
     * @return ShopManager instance
     */
    public ShopManager getShopManager() {
        return shopManager;
    }
    
    /**
     * Get the GUI manager
     * @return ShopGuiManager instance
     */
    public ShopGuiManager getGuiManager() {
        return shopGuiManager;
    }
    
    /**
     * Get the transaction service
     * @return ShopTransactionService instance
     */
    public ShopTransactionService getTransactionService() {
        return transactionService;
    }
    
    /**
     * Get the multiplier service
     * @return MultiplierService instance
     */
    public MultiplierService getMultiplierService() {
        return multiplierService;
    }
    
    /**
     * Get the multiplier API
     * @return MultiplierAPI instance
     */
    public MultiplierAPI getMultiplierAPI() {
        return multiplierAPI;
    }
    
    // --- Utility Methods ---
    
    /**
     * Get the main plugin instance
     * @return BShop plugin instance
     */
    public BShop getPlugin() {
        return plugin;
    }
} 
package net.bumpier.bshop.api;

import org.bukkit.entity.Player;

/**
 * API for GUI operations
 */
public class GUIAPI {
    
    private final BShopAPI api;
    
    public GUIAPI(BShopAPI api) {
        this.api = api;
    }
    
    /**
     * Open the main menu for a player
     * @param player Player to open menu for
     */
    public void openMainMenu(Player player) {
        api.getGuiManager().openMainMenu(player);
    }
    
    /**
     * Open a specific shop for a player
     * @param player Player to open shop for
     * @param shopId Shop ID
     * @param page Page number (0-based)
     */
    public void openShop(Player player, String shopId, int page) {
        api.getGuiManager().openShop(player, shopId, page);
    }
    
    /**
     * Open a shop on the first page
     * @param player Player to open shop for
     * @param shopId Shop ID
     */
    public void openShop(Player player, String shopId) {
        openShop(player, shopId, 0);
    }
    
    /**
     * Open recent purchases menu for a player
     * @param player Player to open menu for
     */
    public void openRecentPurchases(Player player) {
        api.getGuiManager().openRecentPurchasesMenu(player);
    }
    
    /**
     * Check if a player has any bShop GUI open
     * @param player Player to check
     * @return true if player has a bShop GUI open
     */
    public boolean hasShopOpen(Player player) {
        return api.getGuiManager().getOpenPageInfo(player) != null;
    }
    
    /**
     * Close any open bShop GUI for a player
     * @param player Player to close GUI for
     */
    public void closeShopGUI(Player player) {
        if (hasShopOpen(player)) {
            player.closeInventory();
        }
    }
} 
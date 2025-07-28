package net.bumpier.bshop.api;

import org.bukkit.entity.Player;

/**
 * API for multiplier operations
 */
public class MultiplierAPI {
    
    private final BShopAPI api;
    
    public MultiplierAPI(BShopAPI api) {
        this.api = api;
    }
    
    /**
     * Get a player's current multiplier
     * @param player Player to check
     * @return Current multiplier value
     */
    public double getPlayerMultiplier(Player player) {
        return api.getMultiplierService().getPlayerMultiplier(player);
    }
    
    /**
     * Get a formatted string of a player's multiplier
     * @param player Player to check
     * @return Formatted multiplier string (e.g., "1.5x")
     */
    public String getMultiplierDisplay(Player player) {
        return api.getMultiplierService().getMultiplierDisplay(player);
    }
    
    /**
     * Check if multipliers are enabled
     * @return true if multipliers are enabled
     */
    public boolean isEnabled() {
        return api.getMultiplierService().isEnabled();
    }
    
    /**
     * Get the maximum allowed multiplier
     * @return Maximum multiplier value
     */
    public double getMaxMultiplier() {
        return api.getMultiplierService().getMaxMultiplier();
    }
    
    /**
     * Check if multipliers stack
     * @return true if multipliers stack
     */
    public boolean isStackMultipliers() {
        return api.getMultiplierService().isStackMultipliers();
    }
    
    /**
     * Get all configured permission multipliers
     * @return Map of permission to multiplier value
     */
    public java.util.Map<String, Double> getPermissionMultipliers() {
        return api.getMultiplierService().getPermissionMultipliers();
    }
}

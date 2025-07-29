package net.bumpier.bshop.api;

import org.bukkit.entity.Player;
import java.util.UUID;

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
     * Get a formatted string of a player's multiplier by UUID
     * @param playerUuid Player's UUID to check
     * @return Formatted multiplier string (e.g., "1.5x")
     */
    public String getMultiplierDisplay(UUID playerUuid) {
        return api.getMultiplierService().getMultiplierDisplay(playerUuid);
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

    // --- Temporary Multiplier Methods ---

    /**
     * Set a temporary multiplier for a player
     * @param player The player to set the multiplier for
     * @param multiplier The multiplier value (must be > 0)
     * @return true if successful, false if multiplier is invalid
     */
    public boolean setTemporaryMultiplier(Player player, double multiplier) {
        return api.getMultiplierService().setTemporaryMultiplier(player, multiplier);
    }

    /**
     * Set a temporary multiplier for a player by UUID
     * @param playerUuid The player's UUID
     * @param multiplier The multiplier value (must be > 0)
     * @return true if successful, false if multiplier is invalid
     */
    public boolean setTemporaryMultiplier(UUID playerUuid, double multiplier) {
        return api.getMultiplierService().setTemporaryMultiplier(playerUuid, multiplier);
    }

    /**
     * Remove a temporary multiplier from a player
     * @param player The player to remove the multiplier from
     * @return true if a temporary multiplier was removed, false if none existed
     */
    public boolean removeTemporaryMultiplier(Player player) {
        return api.getMultiplierService().removeTemporaryMultiplier(player);
    }

    /**
     * Remove a temporary multiplier from a player by UUID
     * @param playerUuid The player's UUID
     * @return true if a temporary multiplier was removed, false if none existed
     */
    public boolean removeTemporaryMultiplier(UUID playerUuid) {
        return api.getMultiplierService().removeTemporaryMultiplier(playerUuid);
    }

    /**
     * Get a player's temporary multiplier
     * @param player The player to check
     * @return The temporary multiplier value, or null if none exists
     */
    public Double getTemporaryMultiplier(Player player) {
        return api.getMultiplierService().getTemporaryMultiplier(player);
    }

    /**
     * Get a player's temporary multiplier by UUID
     * @param playerUuid The player's UUID
     * @return The temporary multiplier value, or null if none exists
     */
    public Double getTemporaryMultiplier(UUID playerUuid) {
        return api.getMultiplierService().getTemporaryMultiplier(playerUuid);
    }

    /**
     * Check if a player has a temporary multiplier
     * @param player The player to check
     * @return true if the player has a temporary multiplier
     */
    public boolean hasTemporaryMultiplier(Player player) {
        return api.getMultiplierService().hasTemporaryMultiplier(player);
    }

    /**
     * Check if a player has a temporary multiplier by UUID
     * @param playerUuid The player's UUID
     * @return true if the player has a temporary multiplier
     */
    public boolean hasTemporaryMultiplier(UUID playerUuid) {
        return api.getMultiplierService().hasTemporaryMultiplier(playerUuid);
    }

    /**
     * Get all temporary multipliers
     * @return Map of player UUID to temporary multiplier value
     */
    public java.util.Map<UUID, Double> getTemporaryMultipliers() {
        return api.getMultiplierService().getTemporaryMultipliers();
    }

    /**
     * Clear all temporary multipliers
     */
    public void clearTemporaryMultipliers() {
        api.getMultiplierService().clearTemporaryMultipliers();
    }
}

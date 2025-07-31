package net.bumpier.bshop.api;

import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.List;
import java.util.Map;

/**
 * API for multiplier operations
 * Provides access to tier-based and temporary sell price multipliers
 */
public class MultiplierAPI {
    
    private final BShopAPI api;
    
    public MultiplierAPI(BShopAPI api) {
        this.api = api;
    }
    
    // --- Basic Multiplier Methods ---
    
    /**
     * Get a player's current multiplier
     * @param player Player to check
     * @return Current multiplier value
     */
    public double getPlayerMultiplier(Player player) {
        return api.getMultiplierService().getPlayerMultiplier(player);
    }
    
    /**
     * Apply multiplier to a sell price
     * @param player Player to apply multiplier for
     * @param basePrice Base price to multiply
     * @return Price with multiplier applied
     */
    public double applyMultiplier(Player player, double basePrice) {
        return api.getMultiplierService().applyMultiplier(player, basePrice);
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
    public Map<String, Double> getPermissionMultipliers() {
        return api.getMultiplierService().getPermissionMultipliers();
    }

    // --- Tier-Based Multiplier Methods ---
    
    /**
     * Get all available multiplier tiers
     * @return Map of permission to MultiplierTier
     */
    public Map<String, MultiplierTier> getMultiplierTiers() {
        return api.getMultiplierService().getMultiplierTiers();
    }
    
    /**
     * Get tiers organized by category
     * @return Map of category to list of tiers
     */
    public Map<String, List<MultiplierTier>> getTiersByCategory() {
        return api.getMultiplierService().getTiersByCategory();
    }
    
    /**
     * Get a specific tier by permission
     * @param permission Permission string
     * @return MultiplierTier or null if not found
     */
    public MultiplierTier getTier(String permission) {
        return api.getMultiplierService().getTier(permission);
    }
    
    /**
     * Get active tiers for a player
     * @param player Player to check
     * @return List of active tiers (sorted by multiplier value, highest first)
     */
    public List<MultiplierTier> getActiveTiers(Player player) {
        return api.getMultiplierService().getActiveTiers(player);
    }
    
    /**
     * Get the highest tier a player has access to
     * @param player Player to check
     * @return Highest tier or null if none
     */
    public MultiplierTier getHighestTier(Player player) {
        return api.getMultiplierService().getHighestTier(player);
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
     * Set a temporary multiplier with duration and metadata
     * @param playerUuid Player's UUID
     * @param multiplier Multiplier value (must be > 0)
     * @param durationMs Duration in milliseconds (0 for permanent)
     * @param reason Reason for the multiplier
     * @param grantedBy Who granted the multiplier
     * @return true if successful, false if multiplier is invalid
     */
    public boolean setTemporaryMultiplier(UUID playerUuid, double multiplier, long durationMs, String reason, String grantedBy) {
        return api.getMultiplierService().setTemporaryMultiplier(playerUuid, multiplier, durationMs, reason, grantedBy);
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
     * Expire a temporary multiplier immediately
     * @param playerUuid Player's UUID
     * @return true if multipliers were expired, false if none existed
     */
    public boolean expireTemporaryMultiplier(UUID playerUuid) {
        return api.getMultiplierService().expireTemporaryMultiplier(playerUuid);
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
     * Get temporary multiplier data with metadata
     * @param playerUuid Player's UUID
     * @return TemporaryMultiplierData or null if none exists
     */
    public TemporaryMultiplierData getTemporaryMultiplierData(UUID playerUuid) {
        return api.getMultiplierService().getTemporaryMultiplierData(playerUuid);
    }
    
    /**
     * Get all temporary multipliers with metadata
     * @return Map of player UUID to temporary multiplier data
     */
    public Map<UUID, TemporaryMultiplierData> getTemporaryMultipliersData() {
        return api.getMultiplierService().getTemporaryMultipliersData();
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
    public Map<UUID, Double> getTemporaryMultipliers() {
        return api.getMultiplierService().getTemporaryMultipliers();
    }

    /**
     * Clear all temporary multipliers
     */
    public void clearTemporaryMultipliers() {
        api.getMultiplierService().clearTemporaryMultipliers();
    }
    
    /**
     * Get total number of temporary multipliers
     * @return Total count of temporary multipliers
     */
    public long getTotalTemporaryMultipliers() {
        return api.getMultiplierService().getTotalTemporaryMultipliers();
    }
    
    /**
     * Get total number of expired multipliers
     * @return Total count of expired multipliers
     */
    public long getTotalExpiredMultipliers() {
        return api.getMultiplierService().getTotalExpiredMultipliers();
    }
    
    /**
     * Clean up expired multipliers
     */
    public void cleanupExpiredMultipliers() {
        api.getMultiplierService().cleanupExpiredMultipliers();
    }

    // --- Statistics and Information ---
    
    /**
     * Get multiplier statistics
     * @return Map containing various multiplier statistics
     */
    public Map<String, Object> getMultiplierStats() {
        return api.getMultiplierService().getMultiplierStats();
    }
    
    /**
     * Get detailed multiplier information for a player
     * @param player Player to get info for
     * @return MultiplierInfo containing detailed information
     */
    public MultiplierInfo getMultiplierInfo(Player player) {
        return api.getMultiplierService().getMultiplierInfo(player);
    }
    
    /**
     * Get multiplier history for a player
     * @param playerUuid Player's UUID
     * @return List of multiplier history entries
     */
    public List<MultiplierHistoryEntry> getMultiplierHistory(UUID playerUuid) {
        return api.getMultiplierService().getMultiplierHistory(playerUuid);
    }
    
    /**
     * Debug multiplier calculation for a player
     * @param player Player to debug
     */
    public void debugPlayerMultiplier(Player player) {
        api.getMultiplierService().debugPlayerMultiplier(player);
    }
    
    /**
     * Debug configuration issues
     */
    public void debugConfigIssues() {
        api.getMultiplierService().debugConfigIssues();
    }
    
    /**
     * Reload multiplier configuration
     */
    public void reloadMultipliers() {
        api.getMultiplierService().reloadMultipliers();
    }
    
    // --- Data Classes ---
    
    /**
     * Data class for multiplier tiers
     */
    public static class MultiplierTier {
        private final String name;
        private final double multiplier;
        private final String permission;
        private final String category;
        private final String tierId;
        
        public MultiplierTier(String tierId, String category, String name, 
                            double multiplier, String permission) {
            this.tierId = tierId;
            this.category = category;
            this.name = name;
            this.multiplier = multiplier;
            this.permission = permission;
        }
        
        // Getters
        public String getTierId() { return tierId; }
        public String getCategory() { return category; }
        public String getName() { return name; }
        public double getMultiplier() { return multiplier; }
        public String getPermission() { return permission; }
        
        public String getFormattedMultiplier(boolean showPercentage) {
            if (showPercentage) {
                double percentage = (multiplier - 1.0) * 100;
                return String.format("+%.0f%%", percentage);
            }
            return String.format("%.1fx", multiplier);
        }
    }
    
    /**
     * Data class for temporary multipliers with expiration and metadata
     */
    public static class TemporaryMultiplierData {
        private final double multiplier;
        private final long expirationTime;
        private final String reason;
        private final String grantedBy;
        private final long grantedAt;
        
        public TemporaryMultiplierData(double multiplier, long expirationTime, String reason, String grantedBy) {
            this.multiplier = multiplier;
            this.expirationTime = expirationTime;
            this.reason = reason != null ? reason : "No reason provided";
            this.grantedBy = grantedBy != null ? grantedBy : "Console";
            this.grantedAt = System.currentTimeMillis();
        }
        
        // Getters
        public double getMultiplier() { return multiplier; }
        public long getExpirationTime() { return expirationTime; }
        public String getReason() { return reason; }
        public String getGrantedBy() { return grantedBy; }
        public long getGrantedAt() { return grantedAt; }
        
        public boolean isExpired() {
            return expirationTime > 0 && System.currentTimeMillis() > expirationTime;
        }
        
        public long getTimeRemaining() {
            if (expirationTime <= 0) return -1; // Permanent
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }
        
        public String getTimeRemainingFormatted() {
            long remaining = getTimeRemaining();
            if (remaining < 0) return "Permanent";
            if (remaining == 0) return "Expired";
            
            long hours = remaining / (1000 * 60 * 60);
            long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
            long seconds = (remaining % (1000 * 60)) / 1000;
            
            if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }
    
    /**
     * Data class for detailed multiplier information
     */
    public static class MultiplierInfo {
        private double totalMultiplier;
        private List<MultiplierTier> activeTiers;
        private TemporaryMultiplierData temporaryMultiplier;
        private MultiplierTier highestTier;
        
        // Getters and setters
        public double getTotalMultiplier() { return totalMultiplier; }
        public void setTotalMultiplier(double totalMultiplier) { this.totalMultiplier = totalMultiplier; }
        
        public List<MultiplierTier> getActiveTiers() { return activeTiers; }
        public void setActiveTiers(List<MultiplierTier> activeTiers) { this.activeTiers = activeTiers; }
        
        public TemporaryMultiplierData getTemporaryMultiplier() { return temporaryMultiplier; }
        public void setTemporaryMultiplier(TemporaryMultiplierData temporaryMultiplier) { this.temporaryMultiplier = temporaryMultiplier; }
        
        public MultiplierTier getHighestTier() { return highestTier; }
        public void setHighestTier(MultiplierTier highestTier) { this.highestTier = highestTier; }
        
        public String getFormattedTotal() {
            if (totalMultiplier == 1.0) return "1.0x";
            return String.format("%.1fx", totalMultiplier);
        }
        
        public boolean hasTemporaryMultiplier() {
            return temporaryMultiplier != null && !temporaryMultiplier.isExpired();
        }
        
        public boolean hasTierMultipliers() {
            return activeTiers != null && !activeTiers.isEmpty();
        }
    }
    
    /**
     * Multiplier history entry
     */
    public static class MultiplierHistoryEntry {
        public final long timestamp;
        public final double multiplier;
        public final String reason;
        public final String grantedBy;
        
        public MultiplierHistoryEntry(long timestamp, double multiplier, String reason, String grantedBy) {
            this.timestamp = timestamp;
            this.multiplier = multiplier;
            this.reason = reason;
            this.grantedBy = grantedBy;
        }
    }
}

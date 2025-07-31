package net.bumpier.bshop.util;

import net.bumpier.bshop.BShop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;

/**
 * Enhanced service for handling tier-based and temporary sell price multipliers
 * with persistence, expiration, events, and performance optimizations
 */
public class MultiplierService {
    private final BShop plugin;
    private final Map<String, MultiplierTier> multiplierTiers = new HashMap<>();
    private final Map<String, Double> legacyPermissionMultipliers = new HashMap<>();
    private final Map<UUID, List<TemporaryMultiplierData>> temporaryMultipliers = new ConcurrentHashMap<>();
    private final Map<UUID, CachedMultiplier> multiplierCache = new ConcurrentHashMap<>();
    
    // Configuration
    private boolean enabled = false;
    private double maxMultiplier = 10.0;
    private long cacheExpirationTime = 30000; // 30 seconds
    private long cleanupInterval = 60000; // 1 minute
    private String displayFormat = "%multiplier%x";
    private boolean showPercentageBonus = false;
    
    // Add performance monitoring
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalCalculations = new AtomicLong(0);
    private final AtomicLong cleanupRuns = new AtomicLong(0);
    private final AtomicLong expiredMultipliers = new AtomicLong(0);
    
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
    
    public MultiplierService(BShop plugin) {
        this.plugin = plugin;
        loadMultipliers();
        startCleanupTask();
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
     * Cache entry for performance optimization
     */
    public static class CachedMultiplier {
        private final double multiplier;
        private final long lastCalculated;
        private final Set<String> activePermissions;
        private final UUID playerId;
        
        public CachedMultiplier(double multiplier, Set<String> activePermissions, UUID playerId) {
            this.multiplier = multiplier;
            this.lastCalculated = System.currentTimeMillis();
            this.activePermissions = new HashSet<>(activePermissions);
            this.playerId = playerId;
        }
        
        public double getMultiplier() { return multiplier; }
        public long getLastCalculated() { return lastCalculated; }
        public Set<String> getActivePermissions() { return activePermissions; }
        public UUID getPlayerId() { return playerId; }
        
        public boolean isValid(long cacheExpirationTime) {
            return System.currentTimeMillis() - lastCalculated < cacheExpirationTime;
        }
    }
    
    /**
     * Load multiplier configuration from config.yml with validation
     */
    public void loadMultipliers() {
        multiplierTiers.clear();
        legacyPermissionMultipliers.clear();
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("multipliers");
        
        if (config == null) {
            plugin.getLogger().info("No multiplier configuration found, multipliers disabled.");
            enabled = false;
            return;
        }
        
        // Load basic settings first
        enabled = config.getBoolean("enabled", false);
        maxMultiplier = config.getDouble("max_multiplier", 10.0);
        cacheExpirationTime = config.getLong("cache_expiration_ms", 30000);
        cleanupInterval = config.getLong("cleanup_interval_ms", 60000);
        
        // Load cache duration from performance.caching section
        ConfigurationSection cachingConfig = plugin.getConfig().getConfigurationSection("performance.caching");
        if (cachingConfig != null) {
            long multiplierCacheDuration = cachingConfig.getLong("multiplier_cache_duration", 30000);
            if (multiplierCacheDuration > 0) {
                cacheExpirationTime = multiplierCacheDuration;
            }
        }
        
        // Load display settings
        ConfigurationSection displayConfig = config.getConfigurationSection("display");
        if (displayConfig != null) {
            displayFormat = displayConfig.getString("format", "%multiplier%x");
            showPercentageBonus = displayConfig.getBoolean("show_percentage_bonus", false);
        }
        
        if (!enabled) {
            return;
        }
        
        // Load new tier-based system
        loadTierMultipliers(config);
        
        // Load legacy permission multipliers for backward compatibility
        loadLegacyPermissionMultipliers(config);
        
        // Clear cache when configuration changes
        multiplierCache.clear();
    }
    
    /**
     * Validate multiplier configuration
     */
    private boolean validateMultiplierConfiguration(ConfigurationSection config) {
        try {
            // Check max multiplier
            double maxMultiplier = config.getDouble("max_multiplier", 5.0);
            if (maxMultiplier <= 0 || maxMultiplier > 1000) {
                plugin.getLogger().warning("Invalid max_multiplier value: " + maxMultiplier + " (must be 0-1000)");
                return false;
            }
            
            // Check cache expiration
            long cacheExpiration = config.getLong("cache_expiration_ms", 30000);
            if (cacheExpiration < 1000 || cacheExpiration > 300000) {
                plugin.getLogger().warning("Invalid cache_expiration_ms value: " + cacheExpiration + " (must be 1000-300000)");
                return false;
            }
            
            // Check cleanup interval
            long cleanupInterval = config.getLong("cleanup_interval_ms", 60000);
            if (cleanupInterval < 10000 || cleanupInterval > 600000) {
                plugin.getLogger().warning("Invalid cleanup_interval_ms value: " + cleanupInterval + " (must be 10000-600000)");
                return false;
            }
            
            // Validate permission multipliers - but don't fail completely if there are issues
            ConfigurationSection permissionSection = config.getConfigurationSection("permission_multipliers");
            if (permissionSection != null) {
                boolean hasValidEntries = false;
                for (String permission : permissionSection.getKeys(false)) {
                    try {
                        Object value = permissionSection.get(permission);
                        if (value instanceof ConfigurationSection) {
                            plugin.getLogger().warning("Invalid permission multiplier: " + permission + " is a nested section, not a number (skipping)");
                        } else if (value instanceof Number) {
                            double multiplier = ((Number) value).doubleValue();
                            if (multiplier <= 0) {
                                plugin.getLogger().warning("Invalid permission multiplier: " + permission + " = " + multiplier + " (skipping)");
                            } else {
                                hasValidEntries = true;
                            }
                        } else {
                            plugin.getLogger().warning("Invalid permission multiplier: " + permission + " is not a number (skipping)");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Malformed permission multiplier entry: " + permission + " (skipping)");
                    }
                }
                // Don't fail validation if there are no valid permission multipliers
                // The system can still work with temporary multipliers only
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Configuration validation failed", e);
            return false;
        }
    }
    
    /**
     * Load the new tier-based multiplier system
     */
    private void loadTierMultipliers(ConfigurationSection config) {
        ConfigurationSection tiersSection = config.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().info("No tier multipliers configured, using legacy system.");
            return;
        }
        
        int loadedCount = 0;
        int skippedCount = 0;
        
        for (String category : tiersSection.getKeys(false)) {
            ConfigurationSection categorySection = tiersSection.getConfigurationSection(category);
            if (categorySection == null) {
                plugin.getLogger().warning("Invalid tier category: " + category + " (not a section)");
                skippedCount++;
                continue;
            }
            
            for (String tierId : categorySection.getKeys(false)) {
                ConfigurationSection tierSection = categorySection.getConfigurationSection(tierId);
                if (tierSection == null) {
                    plugin.getLogger().warning("Invalid tier: " + category + "." + tierId + " (not a section)");
                    skippedCount++;
                    continue;
                }
                
                try {
                    String name = tierSection.getString("name", tierId);
                    double multiplier = tierSection.getDouble("multiplier", 1.0);
                    String permission = tierSection.getString("permission", "bshop.tier." + tierId);
                    
                    if (multiplier < 0) {
                        plugin.getLogger().warning("Invalid multiplier value for tier " + category + "." + tierId + ": " + multiplier + " (must be >= 0)");
                        skippedCount++;
                        continue;
                    }
                    
                    MultiplierTier tier = new MultiplierTier(tierId, category, name, multiplier, permission);
                    multiplierTiers.put(permission, tier);
                    loadedCount++;
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load tier " + category + "." + tierId + ", skipping...", e);
                    skippedCount++;
                }
            }
        }
        

    }
    
    /**
     * Load legacy permission multipliers for backward compatibility
     */
    private void loadLegacyPermissionMultipliers(ConfigurationSection config) {
        ConfigurationSection legacySection = config.getConfigurationSection("legacy_permission_multipliers");
        if (legacySection == null) {
            return;
        }
        
        int loadedCount = 0;
        for (String permission : legacySection.getKeys(false)) {
            try {
                Object value = legacySection.get(permission);
                if (value instanceof Number) {
                    double multiplier = ((Number) value).doubleValue();
                    if (multiplier >= 0) {
                        legacyPermissionMultipliers.put(permission, multiplier);
                        loadedCount++;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load legacy multiplier for permission: " + permission + ", skipping...", e);
            }
        }
        

    }
    
    /**
     * Start cleanup task for expired multipliers
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredMultipliers();
                    cleanupRuns.incrementAndGet();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during multiplier cleanup", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, cleanupInterval / 50, cleanupInterval / 50);
    }
    
    /**
     * Clean up expired multipliers
     */
    public void cleanupExpiredMultipliers() {
        List<UUID> playersToCheck = new ArrayList<>();
        int cleaned = 0;
        
        try {
            for (Map.Entry<UUID, List<TemporaryMultiplierData>> entry : temporaryMultipliers.entrySet()) {
                List<TemporaryMultiplierData> multipliers = entry.getValue();
                List<TemporaryMultiplierData> expiredMultipliers = new ArrayList<>();
                
                // Find expired multipliers
                for (TemporaryMultiplierData data : multipliers) {
                    if (data.isExpired()) {
                        expiredMultipliers.add(data);
                        cleaned++;
                    }
                }
                
                // Remove expired multipliers
                multipliers.removeAll(expiredMultipliers);
                
                // If no multipliers left, remove the player entry
                if (multipliers.isEmpty()) {
                    playersToCheck.add(entry.getKey());
                }
                
                // Notify player for each expired multiplier
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    for (TemporaryMultiplierData expiredData : expiredMultipliers) {
                        notifyMultiplierExpired(player, expiredData);
                    }
                }
            }
            
            // Remove players with no multipliers left
            for (UUID playerUuid : playersToCheck) {
                temporaryMultipliers.remove(playerUuid);
                multiplierCache.remove(playerUuid);
            }
            
            // Clean expired cache entries
            long cutoff = System.currentTimeMillis() - cacheExpirationTime;
            int cacheCleaned = 0;
            Iterator<Map.Entry<UUID, CachedMultiplier>> cacheIterator = multiplierCache.entrySet().iterator();
            while (cacheIterator.hasNext()) {
                Map.Entry<UUID, CachedMultiplier> entry = cacheIterator.next();
                if (entry.getValue().getLastCalculated() < cutoff) {
                    cacheIterator.remove();
                    cacheCleaned++;
                }
            }
            
            if (cleaned > 0 || cacheCleaned > 0) {
                plugin.getLogger().fine("Multiplier cleanup: removed " + cleaned + " expired multipliers, " + cacheCleaned + " expired cache entries");
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during multiplier cleanup", e);
        }
    }
    
    /**
     * Calculate the total multiplier for a player with caching
     */
    public double getPlayerMultiplier(Player player) {
        if (!enabled) {
            return 1.0;
        }
        
        totalCalculations.incrementAndGet();
        UUID playerId = player.getUniqueId();
        CachedMultiplier cached = multiplierCache.get(playerId);
        
        // Check if cache is valid
        if (cached != null && cached.isValid(cacheExpirationTime)) {
            cacheHits.incrementAndGet();
            return cached.getMultiplier();
        }
        
        cacheMisses.incrementAndGet();
        
        // Calculate new multiplier
        double totalMultiplier = 1.0;
        Set<String> activePermissions = new HashSet<>();
        List<String> debugInfo = new ArrayList<>();
        
        try {
            // Add all tier-based multipliers
            for (MultiplierTier tier : multiplierTiers.values()) {
                if (player.hasPermission(tier.getPermission())) {
                    double beforeTier = totalMultiplier;
                    totalMultiplier += tier.getMultiplier();
                    activePermissions.add(tier.getPermission());
                    debugInfo.add(String.format("Tier %s: %.1f + %.1f = %.1f", 
                        tier.getName(), beforeTier, tier.getMultiplier(), totalMultiplier));
                }
            }
            
            // Add legacy permission multipliers
            for (Map.Entry<String, Double> entry : legacyPermissionMultipliers.entrySet()) {
                if (player.hasPermission(entry.getKey())) {
                    double beforeLegacy = totalMultiplier;
                    totalMultiplier += entry.getValue();
                    activePermissions.add(entry.getKey());
                    debugInfo.add(String.format("Legacy %s: %.1f + %.1f = %.1f", 
                        entry.getKey(), beforeLegacy, entry.getValue(), totalMultiplier));
                }
            }
            
            // Add temporary multipliers
            List<TemporaryMultiplierData> tempMultipliers = temporaryMultipliers.get(playerId);
            if (tempMultipliers != null) {
                for (TemporaryMultiplierData tempData : tempMultipliers) {
                    if (!tempData.isExpired()) {
                        double beforeTemp = totalMultiplier;
                        totalMultiplier += tempData.getMultiplier();
                        debugInfo.add(String.format("Temporary: %.1f + %.1f = %.1f (reason: %s)", 
                            beforeTemp, tempData.getMultiplier(), totalMultiplier, tempData.getReason()));
                    }
                }
            }
            
            // Ensure we don't exceed the maximum multiplier
            double beforeCap = totalMultiplier;
            totalMultiplier = Math.min(totalMultiplier, maxMultiplier);
            if (beforeCap != totalMultiplier) {
                debugInfo.add(String.format("Capped: %.1f -> %.1f (max: %.1f)", 
                    beforeCap, totalMultiplier, maxMultiplier));
            }
            
            // Log debug info if debug is enabled
            if (plugin.getConfig().getBoolean("multipliers.debug_logging", false)) {
                plugin.getLogger().info("Multiplier calculation for " + player.getName() + ":");
                for (String info : debugInfo) {
                    plugin.getLogger().info("  " + info);
                }
                plugin.getLogger().info("  Final multiplier: " + totalMultiplier);
            }
            
            // Cache the result
            multiplierCache.put(playerId, new CachedMultiplier(totalMultiplier, activePermissions, playerId));
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error calculating multiplier for " + player.getName(), e);
            return 1.0; // Return default multiplier on error
        }
        
        return totalMultiplier;
    }
    
    /**
     * Apply multiplier to a sell price
     */
    public double applyMultiplier(Player player, double basePrice) {
        double multiplier = getPlayerMultiplier(player);
        return basePrice * multiplier;
    }
    
    /**
     * Enhanced notifications with detailed placeholders
     */
    private void notifyMultiplierChange(Player player, double oldMultiplier, double newMultiplier, String reason) {
        java.util.Map<String, String> placeholders = new HashMap<>();
        placeholders.put("old_multiplier", String.format("%.1fx", oldMultiplier));
        placeholders.put("new_multiplier", String.format("%.1fx", newMultiplier));
        placeholders.put("reason", reason != null ? reason : "No reason provided");
        
        if (newMultiplier > oldMultiplier) {
            plugin.getMessageService().send(player, "multiplier.increased", placeholders);
        } else if (newMultiplier < oldMultiplier) {
            plugin.getMessageService().send(player, "multiplier.decreased", placeholders);
        }
    }
    
    private void notifyMultiplierExpired(Player player, TemporaryMultiplierData data) {
        java.util.Map<String, String> placeholders = new HashMap<>();
        placeholders.put("multiplier", String.format("%.1fx", data.getMultiplier()));
        placeholders.put("reason", data.getReason());
        placeholders.put("granted_by", data.getGrantedBy());
        
        plugin.getMessageService().send(player, "multiplier.expired", placeholders);
    }
    
    private void notifyMultiplierRemoved(Player player, TemporaryMultiplierData data) {
        java.util.Map<String, String> placeholders = new HashMap<>();
        placeholders.put("multiplier", String.format("%.1fx", data.getMultiplier()));
        placeholders.put("reason", data.getReason());
        placeholders.put("granted_by", data.getGrantedBy());
        
        plugin.getMessageService().send(player, "multiplier.removed", placeholders);
    }
    
    private void notifyMultiplierCleared(Player player) {
        plugin.getMessageService().send(player, "multiplier.cleared");
    }
    
    /**
     * Set a temporary multiplier with enhanced notifications
     */
    public boolean setTemporaryMultiplier(UUID playerUuid, double multiplier, long durationMs, String reason, String grantedBy) {
        if (multiplier < 0) {
            return false;
        }
        
        // Allow multipliers below 1.0x (penalties) but cap at maxMultiplier
        if (multiplier > maxMultiplier) {
            multiplier = maxMultiplier;
        }
        
        long expirationTime = durationMs > 0 ? System.currentTimeMillis() + durationMs : 0;
        TemporaryMultiplierData data = new TemporaryMultiplierData(multiplier, expirationTime, reason, grantedBy);
        
        // Get current total multiplier for notification
        double oldTotalMultiplier = 0.0;
        List<TemporaryMultiplierData> existingMultipliers = temporaryMultipliers.get(playerUuid);
        if (existingMultipliers != null) {
            for (TemporaryMultiplierData existing : existingMultipliers) {
                if (!existing.isExpired()) {
                    oldTotalMultiplier += existing.getMultiplier();
                }
            }
        }
        
        // Add new multiplier to the list
        if (existingMultipliers == null) {
            existingMultipliers = new ArrayList<>();
        }
        existingMultipliers.add(data);
        temporaryMultipliers.put(playerUuid, existingMultipliers);
        multiplierCache.remove(playerUuid); // Clear cache
        
        // Calculate new total multiplier
        double newTotalMultiplier = oldTotalMultiplier + multiplier;
        
        // Enhanced notification with old/new multiplier values
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            notifyMultiplierChange(player, oldTotalMultiplier, newTotalMultiplier, reason);
        }
        
        plugin.getLogger().info("Set temporary multiplier for " + playerUuid + ": " + multiplier + "x (expires: " + 
            (expirationTime > 0 ? new java.util.Date(expirationTime) : "Never") + ")");
        
        return true;
    }
    
    /**
     * Remove a temporary multiplier with enhanced notifications
     */
    public boolean removeTemporaryMultiplier(UUID playerUuid) {
        List<TemporaryMultiplierData> multipliers = temporaryMultipliers.remove(playerUuid);
        if (multipliers != null && !multipliers.isEmpty()) {
            multiplierCache.remove(playerUuid);
            
            // Enhanced notification with removal details (notify for each multiplier)
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                for (TemporaryMultiplierData oldData : multipliers) {
                    if (!oldData.isExpired()) {
                        notifyMultiplierRemoved(player, oldData);
                    }
                }
            }
            
            plugin.getLogger().info("Removed " + multipliers.size() + " temporary multipliers from " + playerUuid);
            return true;
        }
        return false;
    }
    
    /**
     * Expire a temporary multiplier immediately
     */
    public boolean expireTemporaryMultiplier(UUID playerUuid) {
        List<TemporaryMultiplierData> multipliers = temporaryMultipliers.get(playerUuid);
        if (multipliers != null && !multipliers.isEmpty()) {
            // Set expiration time to now for all multipliers
            List<TemporaryMultiplierData> expiredMultipliers = new ArrayList<>();
            for (TemporaryMultiplierData oldData : multipliers) {
                if (!oldData.isExpired()) {
                    TemporaryMultiplierData expiredData = new TemporaryMultiplierData(oldData.getMultiplier(), System.currentTimeMillis(), "Expired by admin", "Admin");
                    expiredMultipliers.add(expiredData);
                }
            }
            
            if (!expiredMultipliers.isEmpty()) {
                temporaryMultipliers.put(playerUuid, expiredMultipliers);
                multiplierCache.remove(playerUuid);
                
                // Enhanced notification
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    for (TemporaryMultiplierData oldData : multipliers) {
                        if (!oldData.isExpired()) {
                            notifyMultiplierExpired(player, oldData);
                        }
                    }
                }
                
                plugin.getLogger().info("Expired " + expiredMultipliers.size() + " temporary multipliers for " + playerUuid);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get temporary multiplier data (returns the first active multiplier for backward compatibility)
     */
    public TemporaryMultiplierData getTemporaryMultiplierData(UUID playerUuid) {
        List<TemporaryMultiplierData> multipliers = temporaryMultipliers.get(playerUuid);
        if (multipliers != null && !multipliers.isEmpty()) {
            // Return the first non-expired multiplier
            for (TemporaryMultiplierData data : multipliers) {
                if (!data.isExpired()) {
                    return data;
                }
            }
        }
        return null;
    }
    
    /**
     * Get all temporary multipliers with metadata (returns first multiplier per player for backward compatibility)
     */
    public Map<UUID, TemporaryMultiplierData> getTemporaryMultipliersData() {
        // Clean expired multipliers first
        cleanupExpiredMultipliers();
        Map<UUID, TemporaryMultiplierData> result = new HashMap<>();
        
        for (Map.Entry<UUID, List<TemporaryMultiplierData>> entry : temporaryMultipliers.entrySet()) {
            List<TemporaryMultiplierData> multipliers = entry.getValue();
            if (multipliers != null && !multipliers.isEmpty()) {
                // Return the first non-expired multiplier for backward compatibility
                for (TemporaryMultiplierData data : multipliers) {
                    if (!data.isExpired()) {
                        result.put(entry.getKey(), data);
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Clear all temporary multipliers with enhanced notifications
     */
    public void clearTemporaryMultipliers() {
        List<UUID> clearedPlayers = new ArrayList<>(temporaryMultipliers.keySet());
        
        temporaryMultipliers.clear();
        multiplierCache.clear();
        
        // Enhanced notifications for all affected players
        for (UUID playerUuid : clearedPlayers) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                notifyMultiplierCleared(player);
            }
        }
        
        plugin.getLogger().info("Cleared all temporary multipliers");
    }
    
    /**
     * Get total number of temporary multipliers
     */
    public long getTotalTemporaryMultipliers() {
        long total = 0;
        for (List<TemporaryMultiplierData> multipliers : temporaryMultipliers.values()) {
            total += multipliers.size();
        }
        return total;
    }
    
    /**
     * Get total number of expired multipliers
     */
    public long getTotalExpiredMultipliers() {
        long total = 0;
        for (List<TemporaryMultiplierData> multipliers : temporaryMultipliers.values()) {
            for (TemporaryMultiplierData data : multipliers) {
                if (data.isExpired()) {
                    total++;
                }
            }
        }
        return total;
    }
    
    /**
     * Get multiplier statistics
     */
    public Map<String, Object> getMultiplierStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Count active temporary multipliers
        long activeCount = 0;
        long expiredCount = 0;
        double totalMultiplierValue = 0.0;
        int totalMultipliers = 0;
        
        for (List<TemporaryMultiplierData> multipliers : temporaryMultipliers.values()) {
            for (TemporaryMultiplierData data : multipliers) {
                if (!data.isExpired()) {
                    activeCount++;
                    totalMultiplierValue += data.getMultiplier();
                    totalMultipliers++;
                } else {
                    expiredCount++;
                }
            }
        }
        
        // Average multiplier value
        double avgMultiplier = totalMultipliers > 0 ? totalMultiplierValue / totalMultipliers : 0.0;
        
        // Cache statistics
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = totalCalculations.get();
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        stats.put("active_temporary_multipliers", activeCount);
        stats.put("expired_multipliers", expiredCount);
        stats.put("permission_multipliers", legacyPermissionMultipliers.size()); // Legacy permission multipliers
        stats.put("average_multiplier", avgMultiplier);
        stats.put("max_multiplier", maxMultiplier);
        stats.put("cache_size", multiplierCache.size());
        stats.put("cache_hits", hits);
        stats.put("cache_misses", misses);
        stats.put("total_calculations", total);
        stats.put("cache_hit_rate_percent", hitRate);
        stats.put("cleanup_runs", cleanupRuns.get());
        stats.put("total_expired_cleaned", expiredMultipliers.get());
        
        return stats;
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
    
    /**
     * Get multiplier history for a player (in-memory only for now)
     */
    public List<MultiplierHistoryEntry> getMultiplierHistory(UUID playerUuid) {
        List<MultiplierHistoryEntry> history = new ArrayList<>();
        
        // For now, return current multiplier if exists
        TemporaryMultiplierData currentData = getTemporaryMultiplierData(playerUuid);
        if (currentData != null) {
            history.add(new MultiplierHistoryEntry(
                currentData.getGrantedAt(),
                currentData.getMultiplier(),
                currentData.getReason(),
                currentData.getGrantedBy()
            ));
        }
        
        return history;
    }
    
    /**
     * Broadcast multiplier event to all players with permission
     */
    public void broadcastMultiplierEvent(String eventType, java.util.Map<String, String> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("bshop.multiplier.notify")) {
                plugin.getMessageService().send(player, "multiplier.broadcast." + eventType, placeholders);
            }
        }
    }
    
    /**
     * Debug method to help identify config issues
     */
    public void debugConfigIssues() {
        plugin.getLogger().info("=== Multiplier Config Debug ===");
        
        // Show the raw config file path
        plugin.getLogger().info("Config file path: " + plugin.getDataFolder() + "/config.yml");
        
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("multipliers");
        if (config == null) {
            plugin.getLogger().warning("No 'multipliers' section found in config.yml");
            return;
        }
        
        plugin.getLogger().info("Enabled: " + config.getBoolean("enabled", false));
        plugin.getLogger().info("Max Multiplier: " + config.getDouble("max_multiplier", 5.0));
        
        ConfigurationSection permissionSection = config.getConfigurationSection("permission_multipliers");
        if (permissionSection == null) {
            plugin.getLogger().warning("No 'permission_multipliers' section found");
            return;
        }
        
        plugin.getLogger().info("Permission multipliers section found with " + permissionSection.getKeys(false).size() + " entries:");
        for (String key : permissionSection.getKeys(false)) {
            try {
                Object value = permissionSection.get(key);
                plugin.getLogger().info("  '" + key + "' = " + value + " (type: " + value.getClass().getSimpleName() + ")");
                
                if (value instanceof ConfigurationSection) {
                    plugin.getLogger().warning("    → INVALID: This is a nested section, not a number!");
                    ConfigurationSection section = (ConfigurationSection) value;
                    plugin.getLogger().warning("    → Contains keys: " + section.getKeys(false));
                    // Show the raw YAML for this section
                    plugin.getLogger().warning("    → Raw value: " + section.getValues(false));
                } else if (value instanceof Number) {
                    double numValue = ((Number) value).doubleValue();
                    if (numValue <= 0) {
                        plugin.getLogger().warning("    → INVALID: Value must be >= 0");
                    } else {
                        plugin.getLogger().info("    → VALID");
                    }
                } else {
                    plugin.getLogger().warning("    → INVALID: Value must be a number");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("  '" + key + "' = ERROR: " + e.getMessage());
            }
        }
        
        // Show all keys in the multipliers section to see if there are any hidden entries
        plugin.getLogger().info("All keys in multipliers section: " + config.getKeys(false));
        
        plugin.getLogger().info("=== End Config Debug ===");
    }
    
    // Legacy methods for backward compatibility
    public boolean setTemporaryMultiplier(Player player, double multiplier) {
        return setTemporaryMultiplier(player.getUniqueId(), multiplier, 0, "Legacy command", "Console");
    }
    
    public boolean setTemporaryMultiplier(UUID playerUuid, double multiplier) {
        return setTemporaryMultiplier(playerUuid, multiplier, 0, "Legacy command", "Console");
    }
    
    public boolean removeTemporaryMultiplier(Player player) {
        return removeTemporaryMultiplier(player.getUniqueId());
    }
    
    public Double getTemporaryMultiplier(Player player) {
        TemporaryMultiplierData data = getTemporaryMultiplierData(player.getUniqueId());
        return data != null ? data.getMultiplier() : null;
    }
    
    public Double getTemporaryMultiplier(UUID playerUuid) {
        TemporaryMultiplierData data = getTemporaryMultiplierData(playerUuid);
        return data != null ? data.getMultiplier() : null;
    }
    
    public boolean hasTemporaryMultiplier(Player player) {
        return getTemporaryMultiplierData(player.getUniqueId()) != null;
    }
    
    public boolean hasTemporaryMultiplier(UUID playerUuid) {
        return getTemporaryMultiplierData(playerUuid) != null;
    }
    
    public Map<UUID, Double> getTemporaryMultipliers() {
        Map<UUID, Double> result = new HashMap<>();
        for (Map.Entry<UUID, List<TemporaryMultiplierData>> entry : temporaryMultipliers.entrySet()) {
            List<TemporaryMultiplierData> multipliers = entry.getValue();
            double totalMultiplier = 0.0;
            for (TemporaryMultiplierData data : multipliers) {
                if (!data.isExpired()) {
                    totalMultiplier += data.getMultiplier();
                }
            }
            if (totalMultiplier > 0) {
                result.put(entry.getKey(), totalMultiplier);
            }
        }
        return result;
    }
    
    // Other existing methods remain the same
    
    public String getMultiplierDisplay(UUID playerUuid) {
        if (!enabled) {
            return "1.0x";
        }
        
        TemporaryMultiplierData data = getTemporaryMultiplierData(playerUuid);
        if (data != null) {
            double multiplier = Math.min(data.getMultiplier(), maxMultiplier);
            if (multiplier == 1.0) {
                return "1.0x";
            }
            return String.format("%.1fx", multiplier);
        }
        
        return "1.0x";
    }
    
    public void debugPlayerMultiplier(Player player) {
        plugin.getLogger().info("=== Multiplier Debug for " + player.getName() + " ===");
        plugin.getLogger().info("Enabled: " + enabled);
        plugin.getLogger().info("Max Multiplier: " + maxMultiplier);
        plugin.getLogger().info("Using additive stacking");
        
        // Force recalculation by clearing cache
        multiplierCache.remove(player.getUniqueId());
        
        TemporaryMultiplierData tempData = getTemporaryMultiplierData(player.getUniqueId());
        if (tempData != null) {
            plugin.getLogger().info("Temporary Multiplier: " + tempData.getMultiplier());
            plugin.getLogger().info("Expiration: " + (tempData.isExpired() ? "Expired" : tempData.getTimeRemainingFormatted()));
            plugin.getLogger().info("Reason: " + tempData.getReason());
            plugin.getLogger().info("Granted by: " + tempData.getGrantedBy());
        } else {
            plugin.getLogger().info("Temporary Multiplier: None");
        }
        
        plugin.getLogger().info("Active Tier Multipliers:");
        for (MultiplierTier tier : multiplierTiers.values()) {
            boolean hasPermission = player.hasPermission(tier.getPermission());
            if (hasPermission) {
                plugin.getLogger().info("  " + tier.getName() + " (" + tier.getPermission() + ") = +" + tier.getMultiplier());
            }
        }
        
        plugin.getLogger().info("Legacy Permission Multipliers:");
        for (Map.Entry<String, Double> entry : legacyPermissionMultipliers.entrySet()) {
            boolean hasPermission = player.hasPermission(entry.getKey());
            plugin.getLogger().info("  " + entry.getKey() + " = +" + entry.getValue() + " (has: " + hasPermission + ")");
        }
        
        plugin.getLogger().info("Temporary Multipliers:");
        List<TemporaryMultiplierData> tempMultipliers = temporaryMultipliers.get(player.getUniqueId());
        if (tempMultipliers != null && !tempMultipliers.isEmpty()) {
            for (TemporaryMultiplierData data : tempMultipliers) {
                if (!data.isExpired()) {
                    plugin.getLogger().info("  " + data.getReason() + " = +" + data.getMultiplier() + " (expires: " + data.getTimeRemainingFormatted() + ")");
                }
            }
        } else {
            plugin.getLogger().info("  None");
        }
        
        // Enable debug logging temporarily for this calculation
        boolean originalDebug = plugin.getConfig().getBoolean("multipliers.debug_logging", false);
        plugin.getConfig().set("multipliers.debug_logging", true);
        
        double finalMultiplier = getPlayerMultiplier(player);
        
        // Restore original debug setting
        plugin.getConfig().set("multipliers.debug_logging", originalDebug);
        
        plugin.getLogger().info("Final Multiplier: " + finalMultiplier + "x");
        plugin.getLogger().info("==========================================");
    }
    
    public boolean isEnabled() { return enabled; }
    public double getMaxMultiplier() { return maxMultiplier; }
    public boolean isStackMultipliers() { return true; }
    public Map<String, Double> getPermissionMultipliers() { return new HashMap<>(legacyPermissionMultipliers); }
    
    public void reloadMultipliers() {
        plugin.getLogger().info("Reloading multiplier configuration...");
        loadMultipliers();
    }

    /**
     * Get all available multiplier tiers
     */
    public Map<String, MultiplierTier> getMultiplierTiers() {
        return new HashMap<>(multiplierTiers);
    }

    /**
     * Get tiers by category
     */
    public Map<String, List<MultiplierTier>> getTiersByCategory() {
        Map<String, List<MultiplierTier>> categories = new HashMap<>();
        
        for (MultiplierTier tier : multiplierTiers.values()) {
            categories.computeIfAbsent(tier.getCategory(), k -> new ArrayList<>()).add(tier);
        }
        
        return categories;
    }

    /**
     * Get a specific tier by permission
     */
    public MultiplierTier getTier(String permission) {
        return multiplierTiers.get(permission);
    }

    /**
     * Get active tiers for a player
     */
    public List<MultiplierTier> getActiveTiers(Player player) {
        List<MultiplierTier> activeTiers = new ArrayList<>();
        
        for (MultiplierTier tier : multiplierTiers.values()) {
            if (player.hasPermission(tier.getPermission())) {
                activeTiers.add(tier);
            }
        }
        
        // Sort by multiplier value (highest first)
        activeTiers.sort((a, b) -> Double.compare(b.getMultiplier(), a.getMultiplier()));
        
        return activeTiers;
    }

    /**
     * Get the highest tier a player has access to
     */
    public MultiplierTier getHighestTier(Player player) {
        List<MultiplierTier> activeTiers = getActiveTiers(player);
        return activeTiers.isEmpty() ? null : activeTiers.get(0);
    }

    /**
     * Get formatted multiplier display for a player
     */
    public String getMultiplierDisplay(Player player) {
        if (!enabled) {
            return "1.0x";
        }
        
        double multiplier = getPlayerMultiplier(player);
        if (multiplier == 1.0) {
            return "1.0x";
        }
        
        if (showPercentageBonus) {
            double percentage = (multiplier - 1.0) * 100;
            return String.format("+%.0f%%", percentage);
        }
        
        return displayFormat.replace("%multiplier%", String.format("%.1f", multiplier));
    }

    /**
     * Get detailed multiplier information for a player
     */
    public MultiplierInfo getMultiplierInfo(Player player) {
        MultiplierInfo info = new MultiplierInfo();
        info.setTotalMultiplier(getPlayerMultiplier(player));
        info.setActiveTiers(getActiveTiers(player));
        info.setTemporaryMultiplier(getTemporaryMultiplierData(player.getUniqueId()));
        info.setHighestTier(getHighestTier(player));
        return info;
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
} 
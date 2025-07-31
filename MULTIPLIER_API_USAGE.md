# bShop Multiplier API Usage Guide

This guide demonstrates how to use the enhanced multiplier API in bShop, which now supports tier-based multipliers, temporary multipliers with metadata, and comprehensive statistics.

## Getting Started

```java
// Get the API instance
BShopAPI api = BShopAPI.getInstance();

// Get the multiplier API
MultiplierAPI multiplierAPI = api.getMultiplierAPI();
```

## Basic Multiplier Operations

### Getting Player Multipliers

```java
Player player = // ... get player

// Get current multiplier value
double multiplier = multiplierAPI.getPlayerMultiplier(player);

// Get formatted display string
String display = multiplierAPI.getMultiplierDisplay(player);

// Apply multiplier to a price
double basePrice = 100.0;
double finalPrice = multiplierAPI.applyMultiplier(player, basePrice);
```

### Configuration Information

```java
// Check if multipliers are enabled
boolean enabled = multiplierAPI.isEnabled();

// Get maximum allowed multiplier
double maxMultiplier = multiplierAPI.getMaxMultiplier();

// Check if multipliers stack
boolean stacks = multiplierAPI.isStackMultipliers();
```

## Tier-Based Multipliers

### Getting Tier Information

```java
// Get all available tiers
Map<String, MultiplierAPI.MultiplierTier> allTiers = multiplierAPI.getMultiplierTiers();

// Get tiers organized by category
Map<String, List<MultiplierAPI.MultiplierTier>> tiersByCategory = multiplierAPI.getTiersByCategory();

// Get a specific tier
MultiplierAPI.MultiplierTier tier = multiplierAPI.getTier("bshop.tier.vip");

// Get active tiers for a player
List<MultiplierAPI.MultiplierTier> activeTiers = multiplierAPI.getActiveTiers(player);

// Get the highest tier a player has
MultiplierAPI.MultiplierTier highestTier = multiplierAPI.getHighestTier(player);
```

### Working with Tier Data

```java
MultiplierAPI.MultiplierTier tier = multiplierAPI.getTier("bshop.tier.vip");
if (tier != null) {
    String tierId = tier.getTierId();           // "vip"
    String category = tier.getCategory();       // "premium"
    String name = tier.getName();               // "VIP"
    double multiplier = tier.getMultiplier();   // 1.5
    String permission = tier.getPermission();   // "bshop.tier.vip"
    
    // Get formatted multiplier
    String formatted = tier.getFormattedMultiplier(false); // "1.5x"
    String percentage = tier.getFormattedMultiplier(true); // "+50%"
}
```

## Temporary Multipliers

### Setting Temporary Multipliers

```java
UUID playerUuid = player.getUniqueId();

// Simple temporary multiplier (permanent)
boolean success = multiplierAPI.setTemporaryMultiplier(playerUuid, 2.0);

// Temporary multiplier with duration and metadata
long durationMs = 3600000; // 1 hour
String reason = "Event bonus";
String grantedBy = "Admin";
boolean success = multiplierAPI.setTemporaryMultiplier(playerUuid, 2.0, durationMs, reason, grantedBy);
```

### Managing Temporary Multipliers

```java
// Check if player has temporary multiplier
boolean hasTemp = multiplierAPI.hasTemporaryMultiplier(playerUuid);

// Get temporary multiplier value
Double tempMultiplier = multiplierAPI.getTemporaryMultiplier(playerUuid);

// Get detailed temporary multiplier data
MultiplierAPI.TemporaryMultiplierData data = multiplierAPI.getTemporaryMultiplierData(playerUuid);
if (data != null) {
    double multiplier = data.getMultiplier();
    long expirationTime = data.getExpirationTime();
    String reason = data.getReason();
    String grantedBy = data.getGrantedBy();
    long grantedAt = data.getGrantedAt();
    
    // Check if expired
    boolean expired = data.isExpired();
    
    // Get time remaining
    long remainingMs = data.getTimeRemaining();
    String remainingFormatted = data.getTimeRemainingFormatted(); // "1h 30m 45s"
}

// Remove temporary multiplier
boolean removed = multiplierAPI.removeTemporaryMultiplier(playerUuid);

// Expire temporary multiplier immediately
boolean expired = multiplierAPI.expireTemporaryMultiplier(playerUuid);
```

### Bulk Operations

```java
// Get all temporary multipliers
Map<UUID, Double> allTempMultipliers = multiplierAPI.getTemporaryMultipliers();

// Get all temporary multipliers with metadata
Map<UUID, MultiplierAPI.TemporaryMultiplierData> allTempData = multiplierAPI.getTemporaryMultipliersData();

// Clear all temporary multipliers
multiplierAPI.clearTemporaryMultipliers();

// Get counts
long totalTemp = multiplierAPI.getTotalTemporaryMultipliers();
long totalExpired = multiplierAPI.getTotalExpiredMultipliers();
```

## Statistics and Information

### Getting Multiplier Statistics

```java
Map<String, Object> stats = multiplierAPI.getMultiplierStats();

// Access various statistics
long activeTemp = (Long) stats.get("active_temporary_multipliers");
long expired = (Long) stats.get("expired_multipliers");
long permissionMultipliers = (Long) stats.get("permission_multipliers");
double avgMultiplier = (Double) stats.get("average_multiplier");
double maxMultiplier = (Double) stats.get("max_multiplier");
long cacheSize = (Long) stats.get("cache_size");
long cacheHits = (Long) stats.get("cache_hits");
long cacheMisses = (Long) stats.get("cache_misses");
long totalCalculations = (Long) stats.get("total_calculations");
double hitRate = (Double) stats.get("cache_hit_rate_percent");
long cleanupRuns = (Long) stats.get("cleanup_runs");
long totalExpiredCleaned = (Long) stats.get("total_expired_cleaned");
```

### Getting Detailed Player Information

```java
MultiplierAPI.MultiplierInfo info = multiplierAPI.getMultiplierInfo(player);

// Access detailed information
double totalMultiplier = info.getTotalMultiplier();
String formattedTotal = info.getFormattedTotal();
List<MultiplierAPI.MultiplierTier> activeTiers = info.getActiveTiers();
MultiplierAPI.TemporaryMultiplierData tempMultiplier = info.getTemporaryMultiplier();
MultiplierAPI.MultiplierTier highestTier = info.getHighestTier();

// Check what types of multipliers the player has
boolean hasTemp = info.hasTemporaryMultiplier();
boolean hasTiers = info.hasTierMultipliers();
```

### Multiplier History

```java
List<MultiplierAPI.MultiplierHistoryEntry> history = multiplierAPI.getMultiplierHistory(playerUuid);

for (MultiplierAPI.MultiplierHistoryEntry entry : history) {
    long timestamp = entry.timestamp;
    double multiplier = entry.multiplier;
    String reason = entry.reason;
    String grantedBy = entry.grantedBy;
}
```

## Debugging and Maintenance

### Debug Methods

```java
// Debug multiplier calculation for a specific player
multiplierAPI.debugPlayerMultiplier(player);

// Debug configuration issues
multiplierAPI.debugConfigIssues();
```

### Maintenance

```java
// Clean up expired multipliers
multiplierAPI.cleanupExpiredMultipliers();

// Reload multiplier configuration
multiplierAPI.reloadMultipliers();
```

## Complete Example

Here's a complete example showing how to use the multiplier API:

```java
public class MultiplierExample {
    
    public void demonstrateMultiplierAPI(Player player) {
        BShopAPI api = BShopAPI.getInstance();
        MultiplierAPI multiplierAPI = api.getMultiplierAPI();
        
        // Check if multipliers are enabled
        if (!multiplierAPI.isEnabled()) {
            player.sendMessage("Multipliers are disabled!");
            return;
        }
        
        // Get current multiplier
        double currentMultiplier = multiplierAPI.getPlayerMultiplier(player);
        String display = multiplierAPI.getMultiplierDisplay(player);
        player.sendMessage("Your current multiplier: " + display);
        
        // Get detailed information
        MultiplierAPI.MultiplierInfo info = multiplierAPI.getMultiplierInfo(player);
        player.sendMessage("Total multiplier: " + info.getFormattedTotal());
        
        // Show active tiers
        List<MultiplierAPI.MultiplierTier> activeTiers = info.getActiveTiers();
        if (!activeTiers.isEmpty()) {
            player.sendMessage("Active tiers:");
            for (MultiplierAPI.MultiplierTier tier : activeTiers) {
                player.sendMessage("  - " + tier.getName() + ": " + tier.getFormattedMultiplier(false));
            }
        }
        
        // Show temporary multiplier
        if (info.hasTemporaryMultiplier()) {
            MultiplierAPI.TemporaryMultiplierData tempData = info.getTemporaryMultiplier();
            player.sendMessage("Temporary multiplier: " + tempData.getMultiplier() + "x");
            player.sendMessage("Reason: " + tempData.getReason());
            player.sendMessage("Time remaining: " + tempData.getTimeRemainingFormatted());
        }
        
        // Apply multiplier to a price
        double basePrice = 100.0;
        double finalPrice = multiplierAPI.applyMultiplier(player, basePrice);
        player.sendMessage("Base price: $" + basePrice + " → Final price: $" + finalPrice);
    }
    
    public void setEventMultiplier(UUID playerUuid, double multiplier, long durationHours) {
        BShopAPI api = BShopAPI.getInstance();
        MultiplierAPI multiplierAPI = api.getMultiplierAPI();
        
        long durationMs = durationHours * 3600000; // Convert hours to milliseconds
        String reason = "Event bonus";
        String grantedBy = "Event System";
        
        boolean success = multiplierAPI.setTemporaryMultiplier(playerUuid, multiplier, durationMs, reason, grantedBy);
        
        if (success) {
            System.out.println("Event multiplier set successfully for " + playerUuid);
        } else {
            System.out.println("Failed to set event multiplier for " + playerUuid);
        }
    }
}
```

## Configuration

The multiplier system is configured in `config.yml` under the `multipliers` section:

```yaml
multipliers:
  enabled: true
  max_multiplier: 10.0
  cache_expiration_ms: 30000
  cleanup_interval_ms: 60000
  
  display:
    format: "%multiplier%x"
    show_percentage_bonus: false
  
  # Tier-based multipliers
  tiers:
    premium:
      vip:
        name: "VIP"
        multiplier: 1.5
        permission: "bshop.tier.vip"
      elite:
        name: "Elite"
        multiplier: 2.0
        permission: "bshop.tier.elite"
  
  # Legacy permission multipliers (for backward compatibility)
  legacy_permission_multipliers:
    "bshop.multiplier.2x": 1.0
    "bshop.multiplier.3x": 2.0
```

This enhanced API provides comprehensive access to all multiplier functionality while maintaining backward compatibility with existing code. 
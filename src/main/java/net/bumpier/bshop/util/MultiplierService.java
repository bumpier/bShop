package net.bumpier.bshop.util;

import net.bumpier.bshop.BShop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Service for handling permission-based sell price multipliers
 */
public class MultiplierService {
    private final BShop plugin;
    private final Map<String, Double> permissionMultipliers = new HashMap<>();
    private boolean enabled = false;
    private double maxMultiplier = 5.0;
    private boolean stackMultipliers = false;

    public MultiplierService(BShop plugin) {
        this.plugin = plugin;
        loadMultipliers();
    }

    /**
     * Load multiplier configuration from config.yml
     */
    public void loadMultipliers() {
        permissionMultipliers.clear();
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("multipliers");
        if (config == null) {
            plugin.getLogger().info("No multiplier configuration found, multipliers disabled.");
            enabled = false;
            return;
        }
        enabled = config.getBoolean("enabled", false);
        if (!enabled) {
            plugin.getLogger().info("Multipliers are disabled in configuration.");
            return;
        }
        maxMultiplier = config.getDouble("max_multiplier", 5.0);
        stackMultipliers = config.getBoolean("stack_multipliers", false);
        ConfigurationSection permissionSection = config.getConfigurationSection("permission_multipliers");
        if (permissionSection == null) {
            plugin.getLogger().warning("No permission multipliers configured!");
            return;
        }
        for (String permission : permissionSection.getKeys(false)) {
            try {
                double multiplier = permissionSection.getDouble(permission);
                if (multiplier > 0) {
                    permissionMultipliers.put(permission, multiplier);
                    plugin.getLogger().info("Loaded multiplier: " + permission + " = " + multiplier + "x");
                } else {
                    plugin.getLogger().warning("Invalid multiplier value for " + permission + ": " + multiplier + " (must be > 0)");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load multiplier for permission: " + permission, e);
            }
        }
        plugin.getLogger().info("Loaded " + permissionMultipliers.size() + " permission multipliers.");
    }

    /**
     * Calculate the total multiplier for a player based on their permissions
     * @param player The player to check
     * @return The total multiplier (1.0 if no multipliers apply)
     */
    public double getPlayerMultiplier(Player player) {
        if (!enabled || permissionMultipliers.isEmpty()) {
            return 1.0;
        }
        if (stackMultipliers) {
            // Stack all applicable multipliers
            double totalMultiplier = 1.0;
            for (Map.Entry<String, Double> entry : permissionMultipliers.entrySet()) {
                if (player.hasPermission(entry.getKey())) {
                    totalMultiplier += entry.getValue();
                }
            }
            return Math.min(totalMultiplier, maxMultiplier);
        } else {
            // Use the highest applicable multiplier
            double highestMultiplier = 1.0;
            for (Map.Entry<String, Double> entry : permissionMultipliers.entrySet()) {
                if (player.hasPermission(entry.getKey())) {
                    highestMultiplier = Math.max(highestMultiplier, 1.0 + entry.getValue());
                }
            }
            return Math.min(highestMultiplier, maxMultiplier);
        }
    }

    /**
     * Apply multiplier to a sell price
     * @param player The player selling the item
     * @param basePrice The base sell price
     * @return The adjusted sell price with multiplier applied
     */
    public double applyMultiplier(Player player, double basePrice) {
        double multiplier = getPlayerMultiplier(player);
        return basePrice * multiplier;
    }

    /**
     * Get a formatted string showing the player's current multiplier
     * @param player The player to check
     * @return Formatted multiplier string
     */
    public String getMultiplierDisplay(Player player) {
        if (!enabled) {
            return "1.0x";
        }
        double multiplier = getPlayerMultiplier(player);
        if (multiplier == 1.0) {
            return "1.0x";
        }
        return String.format("%.1fx", multiplier);
    }

    /**
     * Check if multipliers are enabled
     * @return true if multipliers are enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the maximum allowed multiplier
     * @return The maximum multiplier value
     */
    public double getMaxMultiplier() {
        return maxMultiplier;
    }

    /**
     * Check if multipliers stack
     * @return true if multipliers stack, false if only highest applies
     */
    public boolean isStackMultipliers() {
        return stackMultipliers;
    }

    /**
     * Get all configured permission multipliers
     * @return Map of permission to multiplier value
     */
    public Map<String, Double> getPermissionMultipliers() {
        return new HashMap<>(permissionMultipliers);
    }
} 
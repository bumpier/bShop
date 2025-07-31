package net.bumpier.bshop.shop;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.model.PaginationItem;
import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;

public class ShopManager {

    private final BShop plugin;
    private final Map<String, Shop> loadedShops = new HashMap<>();
    private final File shopsDirectory;
    private final Map<String, Long> nextRotationTimes = new ConcurrentHashMap<>();

    // --- Announcements ---
    private final Map<String, ShopAnnouncement> shopAnnouncements = new ConcurrentHashMap<>();
    private static class ShopAnnouncement {
        final boolean announce;
        final String message;
        ShopAnnouncement(boolean announce, String message) {
            this.announce = announce;
            this.message = message;
        }
    }

    // Add caching fields
    private final Map<String, Shop> shopCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final Map<String, List<ShopItem>> itemCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);
    private long cacheDuration = 300000; // 5 minutes default
    private int maxCachedShops = 100;
    
    // Add performance monitoring
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    public ShopManager(BShop plugin) {
        this.plugin = plugin;
        this.shopsDirectory = new File(plugin.getDataFolder(), "shops");
        
        // Load performance settings
        ConfigurationSection perfConfig = plugin.getConfig().getConfigurationSection("performance.caching");
        if (perfConfig != null) {
            this.cacheDuration = perfConfig.getLong("shop_cache_duration", 300000);
            this.maxCachedShops = perfConfig.getInt("max_cached_shops", 100);
        }
        
        loadShops();
        startCleanupTask();
    }

    private void startCleanupTask() {
        ConfigurationSection perfConfig = plugin.getConfig().getConfigurationSection("performance.memory");
        long cleanupInterval = perfConfig != null ? perfConfig.getLong("cleanup_interval", 300000) : 300000;
        
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                long cutoff = System.currentTimeMillis() - cacheDuration;
                int removedShops = 0;
                int removedItems = 0;
                
                // Clean expired shop cache entries
                Iterator<Map.Entry<String, Shop>> shopIterator = shopCache.entrySet().iterator();
                while (shopIterator.hasNext()) {
                    Map.Entry<String, Shop> entry = shopIterator.next();
                    if (cacheTimestamps.getOrDefault(entry.getKey(), 0L) < cutoff) {
                        shopIterator.remove();
                        cacheTimestamps.remove(entry.getKey());
                        removedShops++;
                    }
                }
                
                // Clean expired item cache entries
                Iterator<Map.Entry<String, List<ShopItem>>> itemIterator = itemCache.entrySet().iterator();
                while (itemIterator.hasNext()) {
                    Map.Entry<String, List<ShopItem>> entry = itemIterator.next();
                    if (cacheTimestamps.getOrDefault(entry.getKey(), 0L) < cutoff) {
                        itemIterator.remove();
                        cacheTimestamps.remove(entry.getKey());
                        removedItems++;
                    }
                }
                
                // Limit cache size with LRU eviction
                if (shopCache.size() > maxCachedShops) {
                    List<String> oldestKeys = cacheTimestamps.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .limit(shopCache.size() - maxCachedShops)
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toList());
                    
                    for (String key : oldestKeys) {
                        shopCache.remove(key);
                        itemCache.remove(key);
                        cacheTimestamps.remove(key);
                        removedShops++;
                    }
                }
                
                // Log cleanup statistics periodically
                if (removedShops > 0 || removedItems > 0) {
                    plugin.getLogger().fine("Cache cleanup: removed " + removedShops + " shops, " + removedItems + " item lists");
                }
                
                // Log cache statistics every 10 minutes
                if (System.currentTimeMillis() % 600000 < cleanupInterval) {
                    long hits = cacheHits.get();
                    long misses = cacheMisses.get();
                    long total = totalRequests.get();
                    double hitRate = total > 0 ? (double) hits / total * 100 : 0;
                    
                    plugin.getLogger().info(String.format("Cache stats: %d hits, %d misses, %.1f%% hit rate, %d cached shops", 
                        hits, misses, hitRate, shopCache.size()));
                }
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during cache cleanup", e);
            }
        }, cleanupInterval / 1000, cleanupInterval / 1000, TimeUnit.SECONDS);
    }

    public void loadShops() {
        loadedShops.clear();
        if (!shopsDirectory.exists()) {
            shopsDirectory.mkdirs();
        }

        // Save default examples if the directory is empty
        if (shopsDirectory.listFiles() == null || shopsDirectory.listFiles().length == 0) {
            plugin.saveResource("shops/rotational_example.yml", false);
            plugin.saveResource("shops/command_based.yml", false);
            plugin.saveResource("shops/advanced_shop.yml", false);
            plugin.saveResource("shops/quick_rotational.yml", false);
        }

        File[] shopFiles = shopsDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (shopFiles == null || shopFiles.length == 0) {
            plugin.getLogger().warning("Could not find any .yml files in the shops directory. No shops will be loaded.");
            return;
        }

        for (File shopFile : shopFiles) {
            String shopId = shopFile.getName().replace(".yml", "");

            FileConfiguration shopConfig = YamlConfiguration.loadConfiguration(shopFile);

            // Check if the file was empty or had a major syntax error
            if (shopConfig.getKeys(false).isEmpty()) {
                plugin.getLogger().warning("   - FAILED: " + shopFile.getName() + " is empty or contains a major YAML syntax error. Skipping.");
                continue;
            }

            String title = shopConfig.getString("title", "Shop");
            int size = Math.max(1, Math.min(6, shopConfig.getInt("size", 3))) * 9;

            Map<String, PaginationItem> paginationItems = loadPaginationItems(shopConfig.getConfigurationSection("pagination"));
            List<ShopItem> items = loadShopItems(shopConfig.getMapList("items"));

            String type = shopConfig.getString("type", null);
            String rotationInterval = null;
            int slots = 0;
            List<ShopItem> activeItems = null;
            List<Integer> itemSlots = null;
            List<ShopItem> featuredItems = null;
            List<Integer> featuredSlots = null;
            boolean announceRotation = false;
            String rotationMessage = null;
            if (type != null && type.equalsIgnoreCase("rotational")) {
                rotationInterval = shopConfig.getString("rotation-interval", "24h");
                slots = shopConfig.getInt("slots", 5);
                // Read item-slots if present
                if (shopConfig.contains("item-slots")) {
                    itemSlots = shopConfig.getIntegerList("item-slots");
                }
                // Read featured-items and featured-slots if present
                if (shopConfig.contains("featured-items")) {
                    featuredItems = loadShopItems(shopConfig.getMapList("featured-items"));
                }
                if (shopConfig.contains("featured-slots")) {
                    featuredSlots = shopConfig.getIntegerList("featured-slots");
                }
                // Announcements
                announceRotation = shopConfig.getBoolean("announce-rotation", false);
                rotationMessage = shopConfig.getString("rotation-message", "<gold>The %shop% shop has rotated!");
                // Randomly select initial active items
                List<ShopItem> shuffled = new ArrayList<>(items);
                java.util.Collections.shuffle(shuffled);
                activeItems = new ArrayList<>(shuffled.subList(0, Math.min(slots, shuffled.size())));
            }

            Shop shop = new Shop(shopId, title, size, paginationItems, items, type, rotationInterval, slots, activeItems, itemSlots, featuredItems, featuredSlots);
            if (type != null && type.equalsIgnoreCase("rotational")) {
                shopAnnouncements.put(shopId, new ShopAnnouncement(announceRotation, rotationMessage));
            }
            loadedShops.put(shopId.toLowerCase(), shop);
        }
    }

    public void startRotationTask(BShop plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    int rotatedShops = 0;
                    
                    for (Shop shop : loadedShops.values()) {
                        if (shop.type() != null && shop.type().equalsIgnoreCase("rotational")) {
                            long next = nextRotationTimes.getOrDefault(shop.id(), 0L);
                            if (now >= next) {
                                // Rotate items
                                List<ShopItem> shuffled = new ArrayList<>(shop.items());
                                Collections.shuffle(shuffled);
                                List<ShopItem> newActive = new ArrayList<>(shuffled.subList(0, Math.min(shop.slots(), shuffled.size())));
                                
                                // Update activeItems via reflection (since record is immutable)
                                try {
                                    java.lang.reflect.Field f = shop.getClass().getDeclaredField("activeItems");
                                    f.setAccessible(true);
                                    f.set(shop, newActive);
                                } catch (Exception ignored) {}
                                
                                // Schedule next rotation
                                long intervalMs = parseInterval(shop.rotationInterval());
                                nextRotationTimes.put(shop.id(), now + intervalMs);
                                
                                // Announce rotation if enabled
                                ShopAnnouncement announcement = shopAnnouncements.get(shop.id());
                                if (announcement != null && announcement.announce) {
                                    String msg = announcement.message.replace("%shop%", shop.title());
                                    Bukkit.broadcastMessage(msg);
                                }
                                
                                rotatedShops++;
                                
                                // Clear cache for this shop since items changed
                                shopCache.remove(shop.id());
                                cacheTimestamps.remove(shop.id());
                            }
                        }
                    }
                    
                    if (rotatedShops > 0) {
                        plugin.getLogger().fine("Rotated " + rotatedShops + " shops");
                    }
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during shop rotation", e);
                }
            }
        }.runTaskTimer(plugin, 20L, 1200L); // Run every minute
    }

    public long parseInterval(String interval) {
        if (interval == null) return 86400000L; // Default 24h
        interval = interval.trim().toLowerCase();
        if (interval.endsWith("h")) {
            return Long.parseLong(interval.replace("h", "")) * 3600000L;
        } else if (interval.endsWith("m")) {
            return Long.parseLong(interval.replace("m", "")) * 60000L;
        } else if (interval.endsWith("s")) {
            return Long.parseLong(interval.replace("s", "")) * 1000L;
        } else {
            try { return Long.parseLong(interval); } catch (Exception e) { return 86400000L; }
        }
    }

    public long getTimeUntilNextRotation(String shopId) {
        long now = System.currentTimeMillis();
        return Math.max(0, nextRotationTimes.getOrDefault(shopId, now) - now);
    }

    private List<ShopItem> loadShopItems(List<Map<?, ?>> itemsList) {
        if (itemsList == null) {
            return Collections.emptyList();
        }
        List<ShopItem> items = new ArrayList<>();
        for (Map<?, ?> itemMap : itemsList) {
            try {
                String id = (String) itemMap.get("id");
                Material material = Material.valueOf(((String) itemMap.get("material")).toUpperCase());
                String displayName = (String) itemMap.get("display-name");
                List<String> lore = (List<String>) itemMap.get("lore");
                int customModelData = itemMap.get("custom-model-data") != null ? (int) itemMap.get("custom-model-data") : 0;
                double buyPrice = itemMap.get("buy-price") != null ? ((Number) itemMap.get("buy-price")).doubleValue() : -1.0;
                double sellPrice = itemMap.get("sell-price") != null ? ((Number) itemMap.get("sell-price")).doubleValue() : -1.0;

                // Parse optional "pinned" item properties
                Integer pinnedPage = (Integer) itemMap.get("page");
                Integer pinnedSlot = (Integer) itemMap.get("slot");

                // Parse command-based fields
                Boolean commandBased = itemMap.get("command-based") != null ? (Boolean) itemMap.get("command-based") : null;
                String buyCommand = itemMap.get("buy-command") != null ? (String) itemMap.get("buy-command") : null;
                String sellCommand = itemMap.get("sell-command") != null ? (String) itemMap.get("sell-command") : null;
                Boolean quantityGui = itemMap.get("quantity-gui") != null ? (Boolean) itemMap.get("quantity-gui") : null;
                String base64Head = itemMap.get("base64-head") != null ? (String) itemMap.get("base64-head") : null;
                String texture = itemMap.get("texture") != null ? (String) itemMap.get("texture") : null;
                String currencyCommand = itemMap.get("currency-command") != null ? (String) itemMap.get("currency-command") : null;
                String currencyRequirement = itemMap.get("currency-requirement") != null ? (String) itemMap.get("currency-requirement") : null;
                
                // Parse separate buy/sell currency commands
                String buyCurrencyCommand = itemMap.get("buy-currency-command") != null ? (String) itemMap.get("buy-currency-command") : null;
                String sellCurrencyCommand = itemMap.get("sell-currency-command") != null ? (String) itemMap.get("sell-currency-command") : null;
                String buyCurrencyRequirement = itemMap.get("buy-currency-requirement") != null ? (String) itemMap.get("buy-currency-requirement") : null;
                String sellCurrencyRequirement = itemMap.get("sell-currency-requirement") != null ? (String) itemMap.get("sell-currency-requirement") : null;
                
                Integer buyLimit = itemMap.get("buy-limit") != null ? (Integer) itemMap.get("buy-limit") : null;
                Integer sellLimit = itemMap.get("sell-limit") != null ? (Integer) itemMap.get("sell-limit") : null;
                items.add(new ShopItem(id, material, displayName, lore, customModelData, buyPrice, sellPrice, pinnedPage, pinnedSlot, commandBased, buyCommand, sellCommand, quantityGui, base64Head, texture, currencyCommand, currencyRequirement, buyCurrencyCommand, sellCurrencyCommand, buyCurrencyRequirement, sellCurrencyRequirement, buyLimit, sellLimit));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to parse an item in a shop file.", e);
            }
        }
        return items;
    }

    private Map<String, PaginationItem> loadPaginationItems(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, PaginationItem> paginationItems = new HashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int slot = section.getInt(key + ".slot", -1);
                
                // Special handling for filler - it doesn't need a specific slot
                if (key.equals("filler")) {
                    Material material = Material.valueOf(section.getString(key + ".material", "GRAY_STAINED_GLASS_PANE").toUpperCase());
                    paginationItems.put(key, new PaginationItem(
                            material,
                            section.getString(key + ".display-name", " "),
                            section.getStringList(key + ".lore"),
                            -1 // Filler uses -1 as a special slot indicator
                    ));
                } else {
                    // ADDED: Validation check for the slot (non-filler items)
                    if (slot < 0) {
                        plugin.getLogger().warning("   - CONFIG ERROR: Pagination item '" + key + "' in shop file is missing a valid 'slot'. This item will be ignored.");
                        continue; // Skip this misconfigured item
                    }
                    Material material = Material.valueOf(section.getString(key + ".material", "STONE").toUpperCase());
                    paginationItems.put(key, new PaginationItem(
                            material,
                            section.getString(key + ".display-name", " "),
                            section.getStringList(key + ".lore"),
                            slot
                    ));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "   - FAILED: Could not parse pagination item '" + key + "'.", e);
            }
        }
        return paginationItems;
    }

    public Shop getShop(String id) {
        totalRequests.incrementAndGet();
        
        // Check cache first
        Shop cached = shopCache.get(id);
        if (cached != null && System.currentTimeMillis() - cacheTimestamps.getOrDefault(id, 0L) < cacheDuration) {
            cacheHits.incrementAndGet();
            return cached;
        }
        
        cacheMisses.incrementAndGet();
        
        // Load from database/file
        Shop shop = loadedShops.get(id.toLowerCase());
        if (shop != null) {
            shopCache.put(id, shop);
            cacheTimestamps.put(id, System.currentTimeMillis());
        }
        return shop;
    }

    /**
     * Returns a copy of the loaded shops map for tab completion and other read-only operations.
     * @return Map of shop ID to Shop object
     */
    public Map<String, Shop> getLoadedShops() {
        return new HashMap<>(loadedShops);
    }

    public void shutdown() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
            }
        }
        
        // Clear caches
        shopCache.clear();
        itemCache.clear();
        cacheTimestamps.clear();
        
        // Log final statistics
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = totalRequests.get();
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        plugin.getLogger().info(String.format("Final cache stats: %d hits, %d misses, %.1f%% hit rate", 
            hits, misses, hitRate));
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_hits", cacheHits.get());
        stats.put("cache_misses", cacheMisses.get());
        stats.put("total_requests", totalRequests.get());
        stats.put("cached_shops", shopCache.size());
        stats.put("cached_items", itemCache.size());
        stats.put("cache_duration_ms", cacheDuration);
        stats.put("max_cached_shops", maxCachedShops);
        
        long total = totalRequests.get();
        if (total > 0) {
            stats.put("hit_rate_percent", (double) cacheHits.get() / total * 100);
        } else {
            stats.put("hit_rate_percent", 0.0);
        }
        
        return stats;
    }
}

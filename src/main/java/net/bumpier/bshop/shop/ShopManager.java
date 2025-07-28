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
            long cutoff = System.currentTimeMillis() - cacheDuration;
            shopCache.entrySet().removeIf(entry -> cacheTimestamps.getOrDefault(entry.getKey(), 0L) < cutoff);
            itemCache.entrySet().removeIf(entry -> cacheTimestamps.getOrDefault(entry.getKey(), 0L) < cutoff);
            
            // Limit cache size
            if (shopCache.size() > maxCachedShops) {
                List<String> oldestKeys = cacheTimestamps.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .limit(shopCache.size() - maxCachedShops)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                
                oldestKeys.forEach(key -> {
                    shopCache.remove(key);
                    itemCache.remove(key);
                    cacheTimestamps.remove(key);
                });
            }
        }, cleanupInterval / 1000, cleanupInterval / 1000, TimeUnit.SECONDS);
    }

    public void loadShops() {
        loadedShops.clear();
        if (!shopsDirectory.exists()) {
            plugin.getLogger().info("Shops directory not found, creating one...");
            shopsDirectory.mkdirs();
        }

        // Save default examples if the directory is empty
        if (shopsDirectory.listFiles() == null || shopsDirectory.listFiles().length == 0) {
            plugin.getLogger().info("Shops directory is empty. Saving default examples (rotational_example.yml, command_based.yml).");
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

        plugin.getLogger().info("Found " + shopFiles.length + " shop file(s). Starting to load...");

        for (File shopFile : shopFiles) {
            String shopId = shopFile.getName().replace(".yml", "");
            plugin.getLogger().info("-> Attempting to load shop '" + shopId + "' from " + shopFile.getName());

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
            plugin.getLogger().info("   - SUCCESS: Successfully loaded shop '" + shopId + "'.");
        }
        plugin.getLogger().info("Shop loading complete. Total loaded: " + loadedShops.size());
    }

    public void startRotationTask(BShop plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Shop shop : loadedShops.values()) {
                    if (shop.type() != null && shop.type().equalsIgnoreCase("rotational")) {
                        long next = nextRotationTimes.getOrDefault(shop.id(), 0L);
                        if (now >= next) {
                            // Rotate items
                            List<ShopItem> shuffled = new ArrayList<>(shop.items());
                            java.util.Collections.shuffle(shuffled);
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
                        }
                    }
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
                Integer buyLimit = itemMap.get("buy-limit") != null ? (Integer) itemMap.get("buy-limit") : null;
                Integer sellLimit = itemMap.get("sell-limit") != null ? (Integer) itemMap.get("sell-limit") : null;
                items.add(new ShopItem(id, material, displayName, lore, customModelData, buyPrice, sellPrice, pinnedPage, pinnedSlot, commandBased, buyCommand, sellCommand, quantityGui, base64Head, texture, currencyCommand, currencyRequirement, buyLimit, sellLimit));
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
        // Check cache first
        Shop cached = shopCache.get(id);
        if (cached != null && System.currentTimeMillis() - cacheTimestamps.getOrDefault(id, 0L) < cacheDuration) {
            return cached;
        }
        
        // Load from database/file
        Shop shop = loadedShops.get(id.toLowerCase());
        if (shop != null) {
            shopCache.put(id, shop);
            cacheTimestamps.put(id, System.currentTimeMillis());
        }
        return shop;
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
    }
}

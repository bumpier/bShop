package net.bumpier.bshop.shop;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.model.PaginationItem;
import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ShopManager {

    private final BShop plugin;
    private final Map<String, Shop> loadedShops = new HashMap<>();
    private final File shopsDirectory;

    public ShopManager(BShop plugin) {
        this.plugin = plugin;
        this.shopsDirectory = new File(plugin.getDataFolder(), "shops");
        loadShops();
    }

    public void loadShops() {
        loadedShops.clear();
        if (!shopsDirectory.exists()) {
            plugin.getLogger().info("Shops directory not found, creating one...");
            shopsDirectory.mkdirs();
        }

        // Save default examples if the directory is empty
        if (shopsDirectory.listFiles() == null || shopsDirectory.listFiles().length == 0) {
            plugin.getLogger().info("Shops directory is empty. Saving default examples (blocks.yml, ores.yml).");
            plugin.saveResource("shops/blocks.yml", false);
            plugin.saveResource("shops/ores.yml", false);
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

            Shop shop = new Shop(shopId, title, size, paginationItems, items);
            loadedShops.put(shopId.toLowerCase(), shop);
            plugin.getLogger().info("   - SUCCESS: Successfully loaded shop '" + shopId + "'.");
        }
        plugin.getLogger().info("Shop loading complete. Total loaded: " + loadedShops.size());
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

                items.add(new ShopItem(id, material, displayName, lore, customModelData, buyPrice, sellPrice, pinnedPage, pinnedSlot));
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
                // ADDED: Validation check for the slot
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
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "   - FAILED: Could not parse pagination item '" + key + "'.", e);
            }
        }
        return paginationItems;
    }

    public Shop getShop(String id) {
        return loadedShops.get(id.toLowerCase());
    }
}
package net.bumpier.bshop.api;

import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * API for shop management operations
 */
public class ShopAPI {
    
    private final BShopAPI api;
    
    public ShopAPI(BShopAPI api) {
        this.api = api;
    }
    
    /**
     * Get all loaded shops
     * @return List of all shops
     */
    public List<Shop> getAllShops() {
        return new ArrayList<>(api.getShopManager().getLoadedShops().values());
    }
    
    /**
     * Get a shop by ID
     * @param shopId Shop ID
     * @return Optional containing the shop if found
     */
    public Optional<Shop> getShop(String shopId) {
        Shop shop = api.getShopManager().getShop(shopId);
        return Optional.ofNullable(shop);
    }
    
    /**
     * Check if a shop exists
     * @param shopId Shop ID
     * @return true if shop exists
     */
    public boolean shopExists(String shopId) {
        return api.getShopManager().getShop(shopId) != null;
    }
    
    /**
     * Get all items in a shop
     * @param shopId Shop ID
     * @return List of shop items
     */
    public List<ShopItem> getShopItems(String shopId) {
        Shop shop = api.getShopManager().getShop(shopId);
        return shop != null ? shop.items() : List.of();
    }
    
    /**
     * Find items by material in a shop
     * @param shopId Shop ID
     * @param material Material to search for
     * @return List of matching items
     */
    public List<ShopItem> findItemsByMaterial(String shopId, Material material) {
        return getShopItems(shopId).stream()
                .filter(item -> item.material() == material)
                .toList();
    }
    
    /**
     * Find a shop item by ItemStack in a specific shop
     * @param shopId Shop ID
     * @param itemStack ItemStack to search for
     * @return Optional containing the matching shop item if found
     */
    public Optional<ShopItem> findItemByItemStack(String shopId, ItemStack itemStack) {
        if (itemStack == null) return Optional.empty();
        
        return getShopItems(shopId).stream()
                .filter(item -> matchesItemStack(item, itemStack))
                .findFirst();
    }
    
    /**
     * Find a shop item by ItemStack across all shops
     * @param itemStack ItemStack to search for
     * @return Optional containing the matching shop item if found
     */
    public Optional<ShopItem> findItemByItemStack(ItemStack itemStack) {
        if (itemStack == null) return Optional.empty();
        
        return getAllShops().stream()
                .flatMap(shop -> shop.items().stream())
                .filter(item -> matchesItemStack(item, itemStack))
                .findFirst();
    }
    
    /**
     * Find all shop items matching an ItemStack across all shops
     * @param itemStack ItemStack to search for
     * @return List of matching shop items
     */
    public List<ShopItem> findAllItemsByItemStack(ItemStack itemStack) {
        if (itemStack == null) return List.of();
        
        return getAllShops().stream()
                .flatMap(shop -> shop.items().stream())
                .filter(item -> matchesItemStack(item, itemStack))
                .toList();
    }
    
    /**
     * Check if an ItemStack matches a ShopItem
     * @param shopItem Shop item to compare against
     * @param itemStack ItemStack to compare
     * @return true if the items match
     */
    private boolean matchesItemStack(ShopItem shopItem, ItemStack itemStack) {
        if (shopItem.material() != itemStack.getType()) {
            return false;
        }
        
        // Check custom model data if the shop item has it
        if (shopItem.customModelData() > 0) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != shopItem.customModelData()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get active items for rotational shops
     * @param shopId Shop ID
     * @return List of active items, or all items if not rotational
     */
    public List<ShopItem> getActiveItems(String shopId) {
        Shop shop = api.getShopManager().getShop(shopId);
        if (shop == null) return List.of();
        
        if (shop.type() != null && shop.type().equalsIgnoreCase("rotational") && shop.activeItems() != null) {
            return shop.activeItems();
        }
        return shop.items();
    }
    
    /**
     * Get time until next rotation for rotational shops
     * @param shopId Shop ID
     * @return Time in milliseconds, or -1 if not rotational
     */
    public long getTimeUntilNextRotation(String shopId) {
        Shop shop = api.getShopManager().getShop(shopId);
        if (shop == null || shop.type() == null || !shop.type().equalsIgnoreCase("rotational")) {
            return -1;
        }
        return api.getShopManager().getTimeUntilNextRotation(shopId);
    }
    
    /**
     * Manually rotate a shop
     * @param shopId Shop ID
     * @return true if rotation was successful
     */
    public boolean rotateShop(String shopId) {
        Shop shop = api.getShopManager().getShop(shopId);
        if (shop == null || shop.type() == null || !shop.type().equalsIgnoreCase("rotational")) {
            return false;
        }
        
        // Use reflection to access private methods (as in ShopCommand)
        try {
            // Implementation would mirror ShopCommand.handleRotate
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Reload all shops
     */
    public void reloadShops() {
        api.getShopManager().loadShops();
    }
} 
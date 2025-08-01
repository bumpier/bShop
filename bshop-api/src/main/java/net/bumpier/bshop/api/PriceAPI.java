package net.bumpier.bshop.api;

import net.bumpier.bshop.shop.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Convenience API for getting prices of items
 * Provides easy access to pricing methods for ItemStacks
 */
public class PriceAPI {
    
    private final BShopAPI api;
    
    public PriceAPI(BShopAPI api) {
        this.api = api;
    }
    
    /**
     * Get the sell price for an ItemStack (searches across all shops)
     * @param player Player selling (for multiplier calculation)
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Sell price, or 0.0 if item not found in any shop
     */
    public double getSellPrice(Player player, ItemStack itemStack, int quantity) {
        return api.getTransactionAPI().getSellPrice(player, itemStack, quantity);
    }
    
    /**
     * Get the sell price for an ItemStack in a specific shop
     * @param player Player selling (for multiplier calculation)
     * @param shopId Shop ID to search in
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Sell price, or 0.0 if item not found in the shop
     */
    public double getSellPrice(Player player, String shopId, ItemStack itemStack, int quantity) {
        return api.getTransactionAPI().getSellPrice(player, shopId, itemStack, quantity);
    }
    
    /**
     * Get the sell price for an ItemStack (base price without multipliers)
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Base sell price, or 0.0 if item not found in any shop
     */
    public double getBaseSellPrice(ItemStack itemStack, int quantity) {
        return api.getTransactionAPI().getBaseSellPrice(itemStack, quantity);
    }
    
    /**
     * Get the sell price for an ItemStack in a specific shop (base price without multipliers)
     * @param shopId Shop ID to search in
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Base sell price, or 0.0 if item not found in the shop
     */
    public double getBaseSellPrice(String shopId, ItemStack itemStack, int quantity) {
        return api.getTransactionAPI().getBaseSellPrice(shopId, itemStack, quantity);
    }
    
    /**
     * Get all possible sell prices for an ItemStack across all shops
     * @param player Player selling (for multiplier calculation)
     * @param itemStack ItemStack to get prices for
     * @param quantity Quantity to sell
     * @return List of sell prices from different shops
     */
    public List<Double> getAllSellPrices(Player player, ItemStack itemStack, int quantity) {
        return api.getTransactionAPI().getAllSellPrices(player, itemStack, quantity);
    }
    
    /**
     * Find a shop item by ItemStack (searches across all shops)
     * @param itemStack ItemStack to search for
     * @return Optional containing the matching shop item if found
     */
    public Optional<ShopItem> findItemByItemStack(ItemStack itemStack) {
        return api.getShopAPI().findItemByItemStack(itemStack);
    }
    
    /**
     * Find a shop item by ItemStack in a specific shop
     * @param shopId Shop ID
     * @param itemStack ItemStack to search for
     * @return Optional containing the matching shop item if found
     */
    public Optional<ShopItem> findItemByItemStack(String shopId, ItemStack itemStack) {
        return api.getShopAPI().findItemByItemStack(shopId, itemStack);
    }
    
    /**
     * Find all shop items matching an ItemStack across all shops
     * @param itemStack ItemStack to search for
     * @return List of matching shop items
     */
    public List<ShopItem> findAllItemsByItemStack(ItemStack itemStack) {
        return api.getShopAPI().findAllItemsByItemStack(itemStack);
    }
    
    /**
     * Check if an ItemStack can be sold (has a sell price > 0)
     * @param itemStack ItemStack to check
     * @return true if the item can be sold
     */
    public boolean canSell(ItemStack itemStack) {
        return getBaseSellPrice(itemStack, 1) > 0.0;
    }
    
    /**
     * Check if an ItemStack can be sold in a specific shop
     * @param shopId Shop ID
     * @param itemStack ItemStack to check
     * @return true if the item can be sold in the shop
     */
    public boolean canSell(String shopId, ItemStack itemStack) {
        return getBaseSellPrice(shopId, itemStack, 1) > 0.0;
    }
} 
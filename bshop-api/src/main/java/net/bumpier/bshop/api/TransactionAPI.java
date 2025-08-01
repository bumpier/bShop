package net.bumpier.bshop.api;

import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.shop.transaction.TransactionResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * API for transaction operations
 */
public class TransactionAPI {
    
    private final BShopAPI api;
    
    public TransactionAPI(BShopAPI api) {
        this.api = api;
    }
    
    /**
     * Buy an item for a player
     * @param player Player buying the item
     * @param item Shop item to buy
     * @param quantity Quantity to buy
     * @return Transaction result
     */
    public TransactionResult buyItem(Player player, ShopItem item, int quantity) {
        try {
            api.getTransactionService().buyItem(player, item, quantity);
            return TransactionResult.SUCCESS;
        } catch (Exception e) {
            return TransactionResult.FAILED;
        }
    }
    
    /**
     * Sell an item for a player
     * @param player Player selling the item
     * @param item Shop item to sell
     * @param quantity Quantity to sell
     * @return Transaction result
     */
    public TransactionResult sellItem(Player player, ShopItem item, int quantity) {
        try {
            api.getTransactionService().sellItem(player, item, quantity);
            return TransactionResult.SUCCESS;
        } catch (Exception e) {
            return TransactionResult.FAILED;
        }
    }
    
    /**
     * Check if a player can buy an item
     * @param player Player to check
     * @param item Item to check
     * @param quantity Quantity to check
     * @return true if player can buy
     */
    public boolean canBuyItem(Player player, ShopItem item, int quantity) {
        // Implementation would check inventory space, balance, limits, etc.
        return true; // Placeholder
    }
    
    /**
     * Check if a player can sell an item
     * @param player Player to check
     * @param item Item to check
     * @param quantity Quantity to check
     * @return true if player can sell
     */
    public boolean canSellItem(Player player, ShopItem item, int quantity) {
        // Implementation would check if player has items, limits, etc.
        return true; // Placeholder
    }
    
    /**
     * Get the price for buying an item (with any applicable multipliers)
     * @param player Player buying
     * @param item Item to buy
     * @param quantity Quantity
     * @return Total price
     */
    public double getBuyPrice(Player player, ShopItem item, int quantity) {
        return item.buyPrice() * quantity;
    }
    
    /**
     * Get the price for selling an item (with any applicable multipliers)
     * @param player Player selling
     * @param item Item to sell
     * @param quantity Quantity
     * @return Total price
     */
    public double getSellPrice(Player player, ShopItem item, int quantity) {
        double basePrice = item.sellPrice() * quantity;
        return api.getMultiplierService().applyMultiplier(player, basePrice);
    }
    
    /**
     * Get the sell price for an ItemStack (searches across all shops)
     * @param player Player selling (for multiplier calculation)
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Sell price, or 0.0 if item not found in any shop
     */
    public double getSellPrice(Player player, ItemStack itemStack, int quantity) {
        Optional<ShopItem> shopItem = api.getShopAPI().findItemByItemStack(itemStack);
        if (shopItem.isPresent()) {
            return getSellPrice(player, shopItem.get(), quantity);
        }
        return 0.0;
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
        Optional<ShopItem> shopItem = api.getShopAPI().findItemByItemStack(shopId, itemStack);
        if (shopItem.isPresent()) {
            return getSellPrice(player, shopItem.get(), quantity);
        }
        return 0.0;
    }
    
    /**
     * Get the sell price for an ItemStack (base price without multipliers)
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Base sell price, or 0.0 if item not found in any shop
     */
    public double getBaseSellPrice(ItemStack itemStack, int quantity) {
        Optional<ShopItem> shopItem = api.getShopAPI().findItemByItemStack(itemStack);
        if (shopItem.isPresent()) {
            return shopItem.get().sellPrice() * quantity;
        }
        return 0.0;
    }
    
    /**
     * Get the sell price for an ItemStack in a specific shop (base price without multipliers)
     * @param shopId Shop ID to search in
     * @param itemStack ItemStack to get price for
     * @param quantity Quantity to sell
     * @return Base sell price, or 0.0 if item not found in the shop
     */
    public double getBaseSellPrice(String shopId, ItemStack itemStack, int quantity) {
        Optional<ShopItem> shopItem = api.getShopAPI().findItemByItemStack(shopId, itemStack);
        if (shopItem.isPresent()) {
            return shopItem.get().sellPrice() * quantity;
        }
        return 0.0;
    }
    
    /**
     * Get all possible sell prices for an ItemStack across all shops
     * @param player Player selling (for multiplier calculation)
     * @param itemStack ItemStack to get prices for
     * @param quantity Quantity to sell
     * @return List of sell prices from different shops
     */
    public List<Double> getAllSellPrices(Player player, ItemStack itemStack, int quantity) {
        List<ShopItem> shopItems = api.getShopAPI().findAllItemsByItemStack(itemStack);
        return shopItems.stream()
                .map(item -> getSellPrice(player, item, quantity))
                .toList();
    }
    
    /**
     * Get player's purchase count for an item in current rotation
     * @param player Player to check
     * @param shopId Shop ID
     * @param itemId Item ID
     * @return Purchase count
     */
    public int getPlayerPurchaseCount(Player player, String shopId, String itemId) {
        // Implementation would access the purchaseCounts map
        return 0; // Placeholder
    }
} 
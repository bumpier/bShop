package net.bumpier.bshop.shop.transaction;

import net.bumpier.bshop.shop.model.ShopItem;

public final class TransactionContext {
    private final ShopItem item;
    private final TransactionType type;
    private int quantity;
    private String sourceShopId;
    private int sourceShopPage;

    public TransactionContext(ShopItem item, TransactionType type) {
        this.item = item;
        this.type = type;
        this.quantity = 1;
        this.sourceShopId = null;
        this.sourceShopPage = 0;
    }
    
    public TransactionContext(ShopItem item, TransactionType type, String sourceShopId, int sourceShopPage) {
        this.item = item;
        this.type = type;
        this.quantity = 1;
        this.sourceShopId = sourceShopId;
        this.sourceShopPage = sourceShopPage;
    }

    // Getters
    public ShopItem getItem() { return item; }
    public TransactionType getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getSourceShopId() { return sourceShopId; }
    public int getSourceShopPage() { return sourceShopPage; }

    // Setters for quantity
    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, quantity); // Ensure quantity is at least 1
    }

    public void addQuantity(int amount) {
        this.quantity = Math.max(1, this.quantity + amount); // Ensure quantity never goes below 1
    }
}
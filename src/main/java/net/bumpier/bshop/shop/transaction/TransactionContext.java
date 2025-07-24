package net.bumpier.bshop.shop.transaction;

import net.bumpier.bshop.shop.model.ShopItem;

public final class TransactionContext {
    private final ShopItem item;
    private final TransactionType type;
    private int quantity;

    public TransactionContext(ShopItem item, TransactionType type) {
        this.item = item;
        this.type = type;
        this.quantity = 1;
    }

    // Getters
    public ShopItem getItem() { return item; }
    public TransactionType getType() { return type; }
    public int getQuantity() { return quantity; }

    // Setters for quantity
    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, quantity); // Ensure quantity is at least 1
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }
}
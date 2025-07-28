package net.bumpier.bshop.api;

/**
 * Result of a transaction operation
 */
public enum TransactionResult {
    SUCCESS,
    FAILED,
    INSUFFICIENT_FUNDS,
    INSUFFICIENT_ITEMS,
    INVENTORY_FULL,
    PURCHASE_LIMIT_REACHED,
    SELL_LIMIT_REACHED,
    ITEM_NOT_AVAILABLE,
    SHOP_NOT_FOUND
} 
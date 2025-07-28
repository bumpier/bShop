package net.bumpier.bshop.api.events;

import net.bumpier.bshop.shop.model.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a shop transaction occurs
 */
public class ShopTransactionEvent extends Event {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final Player player;
    private final ShopItem item;
    private final int quantity;
    private final double price;
    private final TransactionType type;
    private final String shopId;
    private final boolean cancelled;
    
    public ShopTransactionEvent(Player player, ShopItem item, int quantity, double price, TransactionType type, String shopId) {
        this.player = player;
        this.item = item;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
        this.shopId = shopId;
        this.cancelled = false;
    }
    
    public Player getPlayer() { return player; }
    public ShopItem getItem() { return item; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public TransactionType getType() { return type; }
    public String getShopId() { return shopId; }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
    
    public enum TransactionType {
        BUY, SELL
    }
}
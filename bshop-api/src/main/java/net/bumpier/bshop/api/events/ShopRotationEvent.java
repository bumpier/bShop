package net.bumpier.bshop.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a rotational shop rotates
 */
public class ShopRotationEvent extends Event {
    
    private static final HandlerList HANDLERS = new HandlerList();
    
    private final String shopId;
    private final java.util.List<String> oldItems;
    private final java.util.List<String> newItems;
    
    public ShopRotationEvent(String shopId, java.util.List<String> oldItems, java.util.List<String> newItems) {
        this.shopId = shopId;
        this.oldItems = oldItems;
        this.newItems = newItems;
    }
    
    public String getShopId() { return shopId; }
    public java.util.List<String> getOldItems() { return oldItems; }
    public java.util.List<String> getNewItems() { return newItems; }
    
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
} 
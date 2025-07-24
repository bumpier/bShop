package net.bumpier.bshop.shop.ui;

import org.bukkit.inventory.InventoryHolder;

/**
 * A marker interface for all GUIs created by the bShop plugin.
 * This allows for reliable identification of our inventories.
 */
public class BShopGUIHolder implements InventoryHolder {
    @Override
    public org.bukkit.inventory.Inventory getInventory() {
        return null;
    }
}
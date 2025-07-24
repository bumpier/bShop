package net.bumpier.bshop.shop.model;

import org.bukkit.Material;
import java.util.List;
import java.util.Optional;

/**
 * Represents a single, configurable item within a shop.
 * Updated to support optional, pre-defined page and slot for "pinned" items.
 */
public final class ShopItem {
    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final int customModelData;
    private final double buyPrice;
    private final double sellPrice;

    // Optional fields for pinned items
    private final Integer pinnedPage;
    private final Integer pinnedSlot;

    // The final slot this item is assigned to on a generated GUI page.
    private int assignedSlot;

    public ShopItem(String id, Material material, String displayName, List<String> lore, int customModelData, double buyPrice, double sellPrice, Integer pinnedPage, Integer pinnedSlot) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.customModelData = customModelData;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.pinnedPage = pinnedPage;
        this.pinnedSlot = pinnedSlot;
        this.assignedSlot = -1; // Default value
    }

    // --- Getters ---
    public String id() { return id; }
    public Material material() { return material; }
    public String displayName() { return displayName; }
    public List<String> lore() { return lore; }
    public int customModelData() { return customModelData; }
    public double buyPrice() { return buyPrice; }
    public double sellPrice() { return sellPrice; }

    public Optional<Integer> getPinnedPage() { return Optional.ofNullable(pinnedPage); }
    public Optional<Integer> getPinnedSlot() { return Optional.ofNullable(pinnedSlot); }

    public boolean isPinned() {
        return pinnedPage != null && pinnedSlot != null;
    }

    // --- Assigned Slot Management ---
    public int getAssignedSlot() { return assignedSlot; }
    public void setAssignedSlot(int slot) { this.assignedSlot = slot; }
}
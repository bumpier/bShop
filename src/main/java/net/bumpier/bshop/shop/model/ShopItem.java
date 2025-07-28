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

    // Command-based fields
    private final boolean commandBased;
    private final String buyCommand;
    private final String sellCommand;
    private final boolean quantityGui;
    private final String base64Head;
    private final String texture; // NEW
    private final String currencyCommand;
    private final String currencyRequirement;
    private final Integer buyLimit;
    private final Integer sellLimit;

    // The final slot this item is assigned to on a generated GUI page.
    private int assignedSlot;

    public ShopItem(String id, Material material, String displayName, List<String> lore, int customModelData, double buyPrice, double sellPrice, Integer pinnedPage, Integer pinnedSlot, Boolean commandBased, String buyCommand, String sellCommand, Boolean quantityGui, String base64Head, String texture, String currencyCommand, String currencyRequirement, Integer buyLimit, Integer sellLimit) {
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
        this.commandBased = commandBased != null && commandBased;
        this.buyCommand = buyCommand;
        this.sellCommand = sellCommand;
        this.quantityGui = quantityGui != null && quantityGui;
        this.base64Head = base64Head;
        this.texture = texture;
        this.currencyCommand = currencyCommand;
        this.currencyRequirement = currencyRequirement;
        this.buyLimit = buyLimit;
        this.sellLimit = sellLimit;
    }

    // Add legacy constructor for backward compatibility
    public ShopItem(String id, Material material, String displayName, List<String> lore, int customModelData, double buyPrice, double sellPrice, Integer pinnedPage, Integer pinnedSlot, Boolean commandBased, String buyCommand, String sellCommand, Boolean quantityGui, String base64Head, String texture, String currencyCommand, String currencyRequirement) {
        this(id, material, displayName, lore, customModelData, buyPrice, sellPrice, pinnedPage, pinnedSlot, commandBased, buyCommand, sellCommand, quantityGui, base64Head, texture, currencyCommand, currencyRequirement, null, null);
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

    public boolean isCommandBased() { return commandBased; }
    public String getBuyCommand() { return buyCommand; }
    public String getSellCommand() { return sellCommand; }
    public boolean isQuantityGui() { return quantityGui; }
    public String getBase64Head() { return base64Head; }
    public String getTexture() { return texture; }
    public String getCurrencyCommand() { return currencyCommand; }
    public String getCurrencyRequirement() { return currencyRequirement; }
    public Integer getBuyLimit() { return buyLimit; }
    public Integer getSellLimit() { return sellLimit; }

    // --- Assigned Slot Management ---
    public int getAssignedSlot() { return assignedSlot; }
    public void setAssignedSlot(int slot) { this.assignedSlot = slot; }
}
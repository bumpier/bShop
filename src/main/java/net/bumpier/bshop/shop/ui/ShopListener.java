package net.bumpier.bshop.shop.ui;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.model.PaginationItem;
import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.shop.transaction.TransactionContext;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.shop.transaction.TransactionType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ShopListener implements Listener {

    private final ShopGuiManager shopGuiManager;
    private final ShopTransactionService transactionService;
    private final ShopManager shopManager;

    public ShopListener(ShopGuiManager shopGuiManager, ShopTransactionService transactionService, ShopManager shopManager) {
        this.shopGuiManager = shopGuiManager;
        this.transactionService = transactionService;
        this.shopManager = shopManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BShopGUIHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        TransactionContext context = shopGuiManager.getTransactionContext(player);
        
        if (context != null) {
            handleTransactionGuiClick(player, context, clickedItem, event.getInventory());
            return;
        }
        PageInfo pageInfo = shopGuiManager.getOpenPageInfo(player);
        if (pageInfo != null) {
            handlePaginatedShopClick(event, player, pageInfo);
            return;
        }
        if (shopGuiManager.isMainMenu(event.getView())) {
            handleMainMenuClick(event, player, clickedItem);
        }
    }

    private void handleTransactionGuiClick(Player player, TransactionContext context, ItemStack clickedItem, Inventory inventory) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        
        NamespacedKey key = new NamespacedKey(BShop.getInstance(), "bshop_action");
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return;
        }
        String action = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        String[] parts = action.split(":");
        switch (parts[0]) {
            case "add_quantity":
                context.addQuantity(Integer.parseInt(parts[1]));
                shopGuiManager.updateQuantityGui(inventory, context);
                break;
            case "remove_quantity":
                context.addQuantity(-Integer.parseInt(parts[1]));
                shopGuiManager.updateQuantityGui(inventory, context);
                break;
            case "confirm_transaction":
                if (context.getType() == TransactionType.BUY) {
                    transactionService.buyItem(player, context.getItem(), context.getQuantity());
                } else {
                    transactionService.sellItem(player, context.getItem(), context.getQuantity());
                }
                player.closeInventory();
                break;
            case "go_back":
                player.closeInventory();
                break;
            case "open_stacks_menu":
                shopGuiManager.openStackGui(player, context);
                break;
            case "set_quantity_stacks":
                int stackCount = Integer.parseInt(parts[1]);
                int newQuantity = stackCount * context.getItem().material().getMaxStackSize();
                context.setQuantity(newQuantity);
                shopGuiManager.openQuantityGui(player, context);
                break;
            case "open_quantity_menu":
                shopGuiManager.openQuantityGui(player, context);
                break;
        }
    }

    private void handlePaginatedShopClick(InventoryClickEvent event, Player player, PageInfo pageInfo) {
        Shop openShop = shopManager.getShop(pageInfo.shopId());
        if (openShop == null) return;
        int clickedSlot = event.getRawSlot();
        PaginationItem nextPage = openShop.paginationItems().get("next_page");
        if (nextPage != null && clickedSlot == nextPage.slot()) {
            shopGuiManager.openShop(player, pageInfo.shopId(), pageInfo.currentPage() + 1);
            return;
        }
        PaginationItem prevPage = openShop.paginationItems().get("previous_page");
        if (prevPage != null && clickedSlot == prevPage.slot()) {
            shopGuiManager.openShop(player, pageInfo.shopId(), pageInfo.currentPage() - 1);
            return;
        }
        ShopItem shopItem = openShop.items().stream()
                .filter(item -> item.getAssignedSlot() == clickedSlot).findFirst().orElse(null);
        if (shopItem == null) return;
        if (event.getClick() == ClickType.LEFT) {
            shopGuiManager.openQuantityGui(player, shopItem, TransactionType.BUY);
        } else if (event.getClick() == ClickType.RIGHT) {
            shopGuiManager.openQuantityGui(player, shopItem, TransactionType.SELL);
        }
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(BShop.getInstance(), "bshop_action");
        if (container.has(key, PersistentDataType.STRING)) {
            String action = container.get(key, PersistentDataType.STRING);
            if (action != null && action.startsWith("shop:")) {
                String shopId = action.substring(5);
                shopGuiManager.openShop(player, shopId, 0);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BShopGUIHolder && event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            
            // Always clear the shop page info immediately
            shopGuiManager.onGuiClose(player);
            
            // But delay clearing transaction context to allow GUI transitions
            Bukkit.getScheduler().runTaskLater(BShop.getInstance(), () -> {
                // Only clear transaction context if the player doesn't have any BShop GUI open
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof BShopGUIHolder)) {
                    shopGuiManager.clearTransactionContext(player);
                }
            }, 2L); // 2 tick delay to ensure GUI transitions complete
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        shopGuiManager.onGuiClose(event.getPlayer());
        shopGuiManager.clearTransactionContext(event.getPlayer());
    }
}
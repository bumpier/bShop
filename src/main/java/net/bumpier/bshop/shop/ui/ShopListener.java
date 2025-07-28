package net.bumpier.bshop.shop.ui;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.model.PaginationItem;
import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.shop.transaction.TransactionContext;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.shop.transaction.TransactionType;
import net.bumpier.bshop.util.message.MessageService;
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
    private final MessageService messageService;

    public ShopListener(ShopGuiManager shopGuiManager, ShopTransactionService transactionService, ShopManager shopManager, MessageService messageService) {
        this.shopGuiManager = shopGuiManager;
        this.transactionService = transactionService;
        this.shopManager = shopManager;
        this.messageService = messageService;
    }

    private void handleMenuClick(InventoryClickEvent event, Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(BShop.getInstance(), "bshop_action");
        if (!container.has(key, PersistentDataType.STRING)) return;

        String action = container.get(key, PersistentDataType.STRING);

        if (action.startsWith("shop:")) {
            String shopId = action.substring(5);
            shopGuiManager.openShop(player, shopId, 0);
        } else if ("recent_purchases".equals(action)) {
            shopGuiManager.openRecentPurchasesMenu(player);
        } else if ("back_to_main".equals(action)) {
            shopGuiManager.openMainMenu(player);
        } else if ("recent_purchases_next".equals(action) || "recent_purchases_prev".equals(action)) {
            shopGuiManager.handleRecentPurchasesPageAction(player, action);
        }
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
        if (shopGuiManager.isMainMenu(event.getView()) || shopGuiManager.isRecentPurchasesMenu(event.getView())) {
            handleMenuClick(event, player, clickedItem);
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
                shopGuiManager.updateQuantityGui(inventory, player, context);
                break;
            case "remove_quantity":
                context.addQuantity(-Integer.parseInt(parts[1]));
                shopGuiManager.updateQuantityGui(inventory, player, context);
                break;
            case "confirm_transaction":
                if (context.getType() == TransactionType.BUY) {
                    transactionService.buyItem(player, context.getItem(), context.getQuantity());
                } else {
                    transactionService.sellItem(player, context.getItem(), context.getQuantity());
                }
                // Clear transaction context after completing the transaction
                shopGuiManager.clearTransactionContext(player);
                player.closeInventory();
                break;
            case "go_back":
                // Clear the transaction context before navigating back
                shopGuiManager.clearTransactionContext(player);
                
                // Navigate back to the source shop if available
                if (context.getSourceShopId() != null) {
                    shopGuiManager.openShop(player, context.getSourceShopId(), context.getSourceShopPage());
                } else {
                    // Fallback: open main menu if no source shop
                    shopGuiManager.openMainMenu(player);
                }
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
            case "set_quantity_amount":
                int directAmount = Integer.parseInt(parts[1]);
                context.setQuantity(directAmount);
                shopGuiManager.openQuantityGui(player, context);
                break;
            case "open_quantity_menu":
                shopGuiManager.openQuantityGui(player, context);
                break;
            case "back_to_main_menu":
                shopGuiManager.openMainMenu(player);
                break;
        }
    }

    private void handlePaginatedShopClick(InventoryClickEvent event, Player player, PageInfo pageInfo) {
        // Prevent auto-clicks that happen too quickly after shop opens
        if (shopGuiManager.isWithinAutoClickPreventionCooldown(player)) {
            return;
        }
        
        Shop openShop = shopManager.getShop(pageInfo.shopId());
        if (openShop == null) return;
        int clickedSlot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        // --- Next Page ---
        PaginationItem nextPage = openShop.paginationItems().get("next_page");
        if (nextPage != null && clickedSlot == nextPage.slot()) {
            ItemStack expected = shopGuiManager.createPaginationItemStack(nextPage);
            if (clickedItem != null && clickedItem.isSimilar(expected)) {
                shopGuiManager.openShop(player, pageInfo.shopId(), pageInfo.currentPage() + 1);
                return;
            }
        }
        // --- Previous Page ---
        PaginationItem prevPage = openShop.paginationItems().get("previous_page");
        if (prevPage != null && clickedSlot == prevPage.slot()) {
            ItemStack expected = shopGuiManager.createPaginationItemStack(prevPage);
            if (clickedItem != null && clickedItem.isSimilar(expected)) {
                shopGuiManager.openShop(player, pageInfo.shopId(), pageInfo.currentPage() - 1);
                return;
            }
        }
        // --- Back to Menu ---
        PaginationItem backToMenu = openShop.paginationItems().get("back_to_menu");
        if (backToMenu != null && clickedSlot == backToMenu.slot()) {
            ItemStack expected = shopGuiManager.createPaginationItemStack(backToMenu);
            if (clickedItem != null && clickedItem.isSimilar(expected)) {
                shopGuiManager.openMainMenu(player);
                return;
            }
        }
        ShopItem shopItem = openShop.items().stream()
                .filter(item -> item.getAssignedSlot() == clickedSlot).findFirst().orElse(null);
        if (shopItem == null) return;
        if (shopItem.isCommandBased()) {
            // If quantity-gui is enabled, open the quantity GUI
            if (shopItem.isQuantityGui()) {
                if (event.getClick() == ClickType.LEFT && shopItem.getBuyCommand() != null) {
                    shopGuiManager.openQuantityGui(player, shopItem, TransactionType.BUY, pageInfo.shopId(), pageInfo.currentPage());
                } else if (event.getClick() == ClickType.RIGHT && shopItem.getSellCommand() != null) {
                    shopGuiManager.openQuantityGui(player, shopItem, TransactionType.SELL, pageInfo.shopId(), pageInfo.currentPage());
                }
            } else {
                String command = null;
                int amount = 1; // Default amount for command-based
                if (event.getClick() == ClickType.LEFT && shopItem.getBuyCommand() != null) {
                    command = shopItem.getBuyCommand();
                } else if (event.getClick() == ClickType.RIGHT && shopItem.getSellCommand() != null) {
                    command = shopItem.getSellCommand();
                }
                if (command != null) {
                    command = command.replace("%player%", player.getName()).replace("%amount%", String.valueOf(amount)).replace("{quantity}", String.valueOf(amount));
                    if (command.startsWith("/")) command = command.substring(1);
                    if (command.startsWith("console:")) {
                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command.substring(8).trim());
                    } else {
                        player.performCommand(command);
                    }
                }
                event.getWhoClicked().closeInventory();
            }
            return;
        }
        if (event.getClick() == ClickType.LEFT) {
            // Check if item can be bought (has valid buy price)
            if (shopItem.buyPrice() >= 0) {
                shopGuiManager.openQuantityGui(player, shopItem, TransactionType.BUY, pageInfo.shopId(), pageInfo.currentPage());
            } else {
                // Send buy disabled message
                messageService.send(player, "shop.buy_disabled");
            }
        } else if (event.getClick() == ClickType.RIGHT) {
            // Check if item can be sold (has valid sell price)
            if (shopItem.sellPrice() >= 0) {
                shopGuiManager.openQuantityGui(player, shopItem, TransactionType.SELL, pageInfo.shopId(), pageInfo.currentPage());
            } else {
                // Send sell disabled message
                messageService.send(player, "shop.sell_disabled");
            }
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
            } else if ("recent_purchases".equals(action)) {
                shopGuiManager.openRecentPurchasesMenu(player);
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

    // Helper to get a field by reflection (for action field)
    private Object getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
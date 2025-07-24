package net.bumpier.bshop.shop.ui;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.model.PaginationItem;
import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.shop.transaction.TransactionContext;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.shop.transaction.TransactionType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        // Priority 1: Handle active transaction GUIs
        TransactionContext context = shopGuiManager.getTransactionContext(player);
        if (context != null) {
            handleTransactionGuiClick(event, player, context, clickedItem);
            return;
        }

        // Priority 2: Handle paginated shop clicks
        PageInfo pageInfo = shopGuiManager.getOpenPageInfo(player);
        if (pageInfo != null) {
            handlePaginatedShopClick(event, player, pageInfo);
            return;
        }

        // Priority 3: Handle main menu clicks
        if (shopGuiManager.isMainMenu(event.getView())) {
            handleMainMenuClick(event, player, clickedItem);
        }
    }

    private void handleTransactionGuiClick(InventoryClickEvent event, Player player, TransactionContext context, ItemStack clickedItem) {
        event.setCancelled(true);
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey(BShop.getInstance(), "bshop_action");
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // --- DEBUGGING LOGIC ---
        if (!container.has(key, PersistentDataType.STRING)) {
            // If the clicked item has no action tag, send a message to an admin and stop.
            if (player.hasPermission("bshop.admin.debug")) {
                player.sendMessage("§c[bShop Debug] Clicked item has no action tag. (Material: " + clickedItem.getType() + ")");
            }
            return;
        }

        String action = container.get(key, PersistentDataType.STRING);
        String[] parts = action.split(":");

        switch (parts[0]) {
            case "add_quantity":
                context.addQuantity(Integer.parseInt(parts[1]));
                shopGuiManager.updateQuantityGui(event.getInventory(), context);
                break;
            case "remove_quantity":
                context.addQuantity(-Integer.parseInt(parts[1]));
                context.setQuantity(context.getQuantity()); // Enforces minimum of 1
                shopGuiManager.updateQuantityGui(event.getInventory(), context);
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
                int stacks = Integer.parseInt(parts[1]);
                int stackSize = context.getItem().material().getMaxStackSize();
                context.setQuantity(stacks * stackSize);
                shopGuiManager.openQuantityGui(player, context.getItem(), context.getType());
                break;
            case "open_quantity_menu":
                shopGuiManager.openQuantityGui(player, context.getItem(), context.getType());
                break;
        }
    }

    private void handlePaginatedShopClick(InventoryClickEvent event, Player player, PageInfo pageInfo) {
        event.setCancelled(true);
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
                .filter(item -> item.getAssignedSlot() == clickedSlot)
                .findFirst()
                .orElse(null);

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
            event.setCancelled(true);
            String action = container.get(key, PersistentDataType.STRING);
            if (action != null && action.startsWith("shop:")) {
                String shopId = action.substring(5);
                shopGuiManager.openShop(player, shopId, 0);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            shopGuiManager.onGuiClose((Player) event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        shopGuiManager.onGuiClose(event.getPlayer());
    }
}
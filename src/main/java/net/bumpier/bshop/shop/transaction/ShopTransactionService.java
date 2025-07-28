package net.bumpier.bshop.shop.transaction;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.util.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.ConcurrentHashMap;
import net.bumpier.bshop.shop.ShopManager;

public class ShopTransactionService {

    private final BShop plugin;
    private final Economy economy;
    private final MessageService messageService;
    private final ShopGuiManager shopGuiManager;

    // Track purchases per player per rotation: Map<player, Map<shop, Map<item, Map<rotation, amount>>>>
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>>>> purchaseCounts = new ConcurrentHashMap<>();

    public ShopTransactionService(BShop plugin, MessageService messageService, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.economy = plugin.getEconomy();
        this.messageService = messageService;
        this.shopGuiManager = shopGuiManager;
    }

    private long getCurrentRotationTimestamp(String shopId) {
        // Use the ShopManager's nextRotationTimes minus interval as the start of the current rotation
        long now = System.currentTimeMillis();
        ShopManager shopManager = shopGuiManager.getShopManager();
        long next = shopManager.getTimeUntilNextRotation(shopId) + now;
        long interval = shopManager.parseInterval(shopManager.getShop(shopId).rotationInterval());
        return next - interval;
    }

    public void buyItem(Player player, ShopItem item, int quantity) {
        // Enforce buy-limit if set
        Integer buyLimit = item.getBuyLimit();
        if (buyLimit != null) {
            String playerId = player.getUniqueId().toString();
            String shopId = null;
            var pageInfo = shopGuiManager.getOpenPageInfo(player);
            if (pageInfo != null) shopId = pageInfo.shopId();
            if (shopId != null) {
                long rotation = getCurrentRotationTimestamp(shopId);
                purchaseCounts.putIfAbsent(playerId, new ConcurrentHashMap<>());
                purchaseCounts.get(playerId).putIfAbsent(shopId, new ConcurrentHashMap<>());
                purchaseCounts.get(playerId).get(shopId).putIfAbsent(item.id(), new ConcurrentHashMap<>());
                int soFar = purchaseCounts.get(playerId).get(shopId).get(item.id()).getOrDefault(rotation, 0);
                if (soFar + quantity > buyLimit) {
                    messageService.send(player, "shop.purchase_limit_reached");
                    return;
                }
                purchaseCounts.get(playerId).get(shopId).get(item.id()).put(rotation, soFar + quantity);
            }
        }
        // Check for currency-command (prioritize over Vault)
        String currencyCommand = item.getCurrencyCommand();
        String currencyRequirement = item.getCurrencyRequirement();
        if (currencyCommand != null && !currencyCommand.isEmpty()) {
            // Check requirement if present
            if (currencyRequirement != null && !currencyRequirement.isEmpty()) {
                boolean papiPresent = false;
                try {
                    Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    papiPresent = org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
                } catch (ClassNotFoundException ignored) {}
                if (papiPresent) {
                    try {
                        Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                        java.lang.reflect.Method setPlaceholders = papi.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class);
                        String valueStr = (String) setPlaceholders.invoke(null, player, currencyRequirement);
                        int value = Integer.parseInt(valueStr.replaceAll("[^0-9]", ""));
                        int required = quantity;
                        if (value < required) {
                            messageService.send(player, "shop.insufficient_custom_currency");
                            return;
                        }
                    } catch (Exception e) {
                        messageService.send(player, "shop.insufficient_custom_currency");
                        return;
                    }
                } else {
                    // If PlaceholderAPI is not present, allow by default (or could block with a warning)
                }
            }
            String command = currencyCommand.replace("%player%", player.getName()).replace("%amount%", String.valueOf(quantity));
            if (command.startsWith("/")) command = command.substring(1);
            // Always run from console by default
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command);
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("amount", String.valueOf(quantity));
            placeholders.put("item", item.displayName());
            placeholders.put("price", String.format("%,.2f", item.buyPrice() * quantity));
            messageService.send(player, "shop.buy_success", placeholders);
            // Record transaction
            {
                String shopId = null;
                var pageInfo = shopGuiManager.getOpenPageInfo(player);
                if (pageInfo != null) shopId = pageInfo.shopId();
                if (shopId == null) shopId = "unknown";
                shopGuiManager.recordRecentTransaction(player, shopId, item.displayName(), item.material().name(), quantity, item.buyPrice() * quantity, "Buy", item.id());
            }
            return;
        }
        
        // Vault-based economy
        if (economy == null) {
            messageService.send(player, "shop.economy_not_available");
            return;
        }
        if (item.buyPrice() <= 0) {
            messageService.send(player, "shop.buy_disabled");
            return;
        }
        double totalPrice = item.buyPrice() * quantity;
        double balance = economy.getBalance(player);
        if (balance < totalPrice) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("price", String.format("%,.2f", totalPrice));
            placeholders.put("balance", String.format("%,.2f", balance));
            messageService.send(player, "shop.insufficient_funds", placeholders);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) { // Basic check, can be improved
            messageService.send(player, "shop.inventory_full");
            return;
        }
        economy.withdrawPlayer(player, totalPrice);
        player.getInventory().addItem(new ItemStack(item.material(), quantity));
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("amount", String.valueOf(quantity));
        placeholders.put("item", item.displayName());
        placeholders.put("price", String.format("%,.2f", totalPrice));
        messageService.send(player, "shop.buy_success", placeholders);
        // Record transaction
        {
            String shopId = null;
            var pageInfo = shopGuiManager.getOpenPageInfo(player);
            if (pageInfo != null) shopId = pageInfo.shopId();
            if (shopId == null) shopId = "unknown";
            shopGuiManager.recordRecentTransaction(player, shopId, item.displayName(), item.material().name(), quantity, totalPrice, "Buy", item.id());
        }
    }

    public void sellItem(Player player, ShopItem item, int quantity) {
        // Enforce sell-limit if set
        Integer sellLimit = item.getSellLimit();
        if (sellLimit != null) {
            String playerId = player.getUniqueId().toString();
            String shopId = null;
            var pageInfo = shopGuiManager.getOpenPageInfo(player);
            if (pageInfo != null) shopId = pageInfo.shopId();
            if (shopId != null) {
                long rotation = getCurrentRotationTimestamp(shopId);
                purchaseCounts.putIfAbsent(playerId, new ConcurrentHashMap<>());
                purchaseCounts.get(playerId).putIfAbsent(shopId, new ConcurrentHashMap<>());
                purchaseCounts.get(playerId).get(shopId).putIfAbsent(item.id(), new ConcurrentHashMap<>());
                int soFar = purchaseCounts.get(playerId).get(shopId).get(item.id()).getOrDefault(rotation, 0);
                if (soFar + quantity > sellLimit) {
                    messageService.send(player, "shop.sell_limit_reached");
                    return;
                }
                purchaseCounts.get(playerId).get(shopId).get(item.id()).put(rotation, soFar + quantity);
            }
        }
        // Check for currency-command
        String currencyCommand = item.getCurrencyCommand();
        String currencyRequirement = item.getCurrencyRequirement();
        if (currencyCommand != null && !currencyCommand.isEmpty()) {
            // Check requirement if present
            if (currencyRequirement != null && !currencyRequirement.isEmpty()) {
                boolean papiPresent = false;
                try {
                    Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    papiPresent = org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
                } catch (ClassNotFoundException ignored) {}
                if (papiPresent) {
                    try {
                        Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                        java.lang.reflect.Method setPlaceholders = papi.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class);
                        String valueStr = (String) setPlaceholders.invoke(null, player, currencyRequirement);
                        int value = Integer.parseInt(valueStr.replaceAll("[^0-9]", ""));
                        int required = quantity;
                        if (value < required) {
                            messageService.send(player, "shop.insufficient_custom_currency");
                            return;
                        }
                    } catch (Exception e) {
                        messageService.send(player, "shop.insufficient_custom_currency");
                        return;
                    }
                } else {
                    // If PlaceholderAPI is not present, allow by default (or could block with a warning)
                }
            }
            String command = currencyCommand.replace("%player%", player.getName()).replace("%amount%", String.valueOf(quantity));
            if (command.startsWith("/")) command = command.substring(1);
            // Always run from console by default
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command);
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("amount", String.valueOf(quantity));
            placeholders.put("item", item.displayName());
            placeholders.put("price", String.format("%,.2f", item.sellPrice() * quantity));
            messageService.send(player, "shop.sell_success", placeholders);
            // Record transaction
            {
                String shopId = null;
                var pageInfo = shopGuiManager.getOpenPageInfo(player);
                if (pageInfo != null) shopId = pageInfo.shopId();
                if (shopId == null) shopId = "unknown";
                shopGuiManager.recordRecentTransaction(player, shopId, item.displayName(), item.material().name(), quantity, item.sellPrice() * quantity, "Sell", item.id());
            }
            return;
        }
        // Vault-based economy
        if (economy == null) {
            messageService.send(player, "shop.economy_not_available");
            return;
        }
        if (item.sellPrice() <= 0) {
            messageService.send(player, "shop.sell_disabled");
            return;
        }
        ItemStack toSell = new ItemStack(item.material(), quantity);
        if (!player.getInventory().containsAtLeast(toSell, quantity)) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("amount", String.valueOf(quantity));
            placeholders.put("item", item.displayName());
            messageService.send(player, "shop.item_not_owned", placeholders);
            return;
        }
        player.getInventory().removeItem(toSell);
        double totalPrice = item.sellPrice() * quantity;
        economy.depositPlayer(player, totalPrice);
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("amount", String.valueOf(quantity));
        placeholders.put("item", item.displayName());
        placeholders.put("price", String.format("%,.2f", totalPrice));
        messageService.send(player, "shop.sell_success", placeholders);
        // Record transaction
        {
            String shopId = null;
            var pageInfo = shopGuiManager.getOpenPageInfo(player);
            if (pageInfo != null) shopId = pageInfo.shopId();
            if (shopId == null) shopId = "unknown";
            shopGuiManager.recordRecentTransaction(player, shopId, item.displayName(), item.material().name(), quantity, totalPrice, "Sell", item.id());
        }
    }

    // Helper methods for buy/sell currency commands
    private boolean hasItemsInInventory(Player player, org.bukkit.Material material, int quantity) {
        ItemStack itemStack = new ItemStack(material, quantity);
        return player.getInventory().containsAtLeast(itemStack, quantity);
    }

    private void removeItemsFromInventory(Player player, org.bukkit.Material material, int quantity) {
        ItemStack itemStack = new ItemStack(material, quantity);
        player.getInventory().removeItem(itemStack);
    }
}
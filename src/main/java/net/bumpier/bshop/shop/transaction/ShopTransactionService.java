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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.stream.Collectors;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;

public class ShopTransactionService {

    private final BShop plugin;
    private final Economy economy;
    private final MessageService messageService;
    private final ShopGuiManager shopGuiManager;

    // Add async processing
    private final ExecutorService transactionExecutor;
    private final Map<UUID, Long> lastTransactionTime = new ConcurrentHashMap<>();
    private long transactionCooldown = 100; // Configurable cooldown, default 100ms
    
    // Add performance monitoring
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successfulTransactions = new AtomicLong(0);
    private final AtomicLong failedTransactions = new AtomicLong(0);
    private final AtomicLong asyncTransactions = new AtomicLong(0);
    
    // Add transaction optimization settings
    private boolean enableRateLimiting = true;
    private int maxConcurrentTransactions = 50;
    private long transactionTimeoutMs = 5000;
    private boolean enableRetryOnFailure = true;
    private int maxRetryAttempts = 3;

    // Track purchases per player per rotation: Map<player, Map<shop, Map<item, Map<rotation, amount>>>>
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>>>> purchaseCounts = new ConcurrentHashMap<>();

    public ShopTransactionService(BShop plugin, MessageService messageService, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.economy = plugin.getEconomy();
        this.messageService = messageService;
        this.shopGuiManager = shopGuiManager;
        
        // Load cooldown settings from config
        loadCooldownSettings();
        
        // Initialize thread pool based on config
        ConfigurationSection perfConfig = plugin.getConfig().getConfigurationSection("performance.async");
        int threadCount = perfConfig != null ? perfConfig.getInt("transaction_threads", 4) : 4;
        boolean enableThreadNaming = perfConfig != null ? perfConfig.getBoolean("enable_thread_naming", true) : true;
        int threadPriority = perfConfig != null ? perfConfig.getInt("thread_priority", 5) : 5;
        
        if (enableThreadNaming) {
            this.transactionExecutor = Executors.newFixedThreadPool(threadCount, r -> {
                Thread t = new Thread(r, "bShop-Transaction-" + System.currentTimeMillis());
                t.setDaemon(true);
                t.setPriority(threadPriority);
                return t;
            });
        } else {
            this.transactionExecutor = Executors.newFixedThreadPool(threadCount);
        }
    }
    
    private void loadCooldownSettings() {
        ConfigurationSection cooldownConfig = plugin.getConfig().getConfigurationSection("performance.cooldowns");
        if (cooldownConfig != null) {
            this.transactionCooldown = cooldownConfig.getLong("transaction", 100);
        }
        
        // Load transaction optimization settings
        ConfigurationSection txConfig = plugin.getConfig().getConfigurationSection("performance.transaction");
        if (txConfig != null) {
            this.enableRateLimiting = txConfig.getBoolean("enable_rate_limiting", true);
            this.maxConcurrentTransactions = txConfig.getInt("max_concurrent_transactions", 50);
            this.transactionTimeoutMs = txConfig.getLong("transaction_timeout_ms", 5000);
            this.enableRetryOnFailure = txConfig.getBoolean("enable_retry_on_failure", true);
            this.maxRetryAttempts = txConfig.getInt("max_retry_attempts", 3);
        }
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
        totalTransactions.incrementAndGet();
        
        try {
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
                        failedTransactions.incrementAndGet();
                        return;
                    }
                    purchaseCounts.get(playerId).get(shopId).get(item.id()).put(rotation, soFar + quantity);
                }
            }
            
            // Check for currency-command (prioritize over Vault)
            // Prioritize separate buy currency command over legacy currency command
            String currencyCommand = item.getBuyCurrencyCommand();
            String currencyRequirement = item.getBuyCurrencyRequirement();
            
            // Fallback to legacy currency command if separate buy command not set
            if (currencyCommand == null || currencyCommand.isEmpty()) {
                currencyCommand = item.getCurrencyCommand();
                currencyRequirement = item.getCurrencyRequirement();
            }
            
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
                                failedTransactions.incrementAndGet();
                                return;
                            }
                        } catch (Exception e) {
                            messageService.send(player, "shop.insufficient_custom_currency");
                            failedTransactions.incrementAndGet();
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
                successfulTransactions.incrementAndGet();
                return;
            }
            
            // Vault-based economy
            if (economy == null) {
                messageService.send(player, "shop.economy_not_available");
                failedTransactions.incrementAndGet();
                return;
            }
            if (item.buyPrice() <= 0) {
                messageService.send(player, "shop.buy_disabled");
                failedTransactions.incrementAndGet();
                return;
            }
            double totalPrice = item.buyPrice() * quantity;
            double balance = economy.getBalance(player);
            if (balance < totalPrice) {
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("price", String.format("%,.2f", totalPrice));
                placeholders.put("balance", String.format("%,.2f", balance));
                messageService.send(player, "shop.insufficient_funds", placeholders);
                failedTransactions.incrementAndGet();
                return;
            }
            if (player.getInventory().firstEmpty() == -1) { // Basic check, can be improved
                messageService.send(player, "shop.inventory_full");
                failedTransactions.incrementAndGet();
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
            successfulTransactions.incrementAndGet();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during buy transaction for " + player.getName(), e);
            failedTransactions.incrementAndGet();
            messageService.send(player, "shop.transaction_error");
        }
    }

    public CompletableFuture<Boolean> buyItemAsync(Player player, ShopItem item, int quantity) {
        asyncTransactions.incrementAndGet();
        
        // Rate limiting
        if (enableRateLimiting) {
            long now = System.currentTimeMillis();
            long lastTime = lastTransactionTime.getOrDefault(player.getUniqueId(), 0L);
            if (now - lastTime < transactionCooldown) {
                return CompletableFuture.completedFuture(false);
            }
            lastTransactionTime.put(player.getUniqueId(), now);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                buyItem(player, item, quantity);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Async buy transaction failed for " + player.getName(), e);
                failedTransactions.incrementAndGet();
                return false;
            }
        }, transactionExecutor).orTimeout(transactionTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void sellItem(Player player, ShopItem item, int quantity) {
        totalTransactions.incrementAndGet();
        
        try {
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
                        failedTransactions.incrementAndGet();
                        return;
                    }
                    purchaseCounts.get(playerId).get(shopId).get(item.id()).put(rotation, soFar + quantity);
                }
            }
            
            // Check for currency-command
            // Prioritize separate sell currency command over legacy currency command
            String currencyCommand = item.getSellCurrencyCommand();
            String currencyRequirement = item.getSellCurrencyRequirement();
            
            // Fallback to legacy currency command if separate sell command not set
            if (currencyCommand == null || currencyCommand.isEmpty()) {
                currencyCommand = item.getCurrencyCommand();
                currencyRequirement = item.getCurrencyRequirement();
            }
            
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
                                failedTransactions.incrementAndGet();
                                return;
                            }
                        } catch (Exception e) {
                            messageService.send(player, "shop.insufficient_custom_currency");
                            failedTransactions.incrementAndGet();
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
                
                // Apply multiplier to currency command price
                double basePrice = item.sellPrice() * quantity;
                double multiplier = plugin.getMultiplierService().getPlayerMultiplier(player);
                double totalPrice = basePrice * multiplier;
                
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("amount", String.valueOf(quantity));
                placeholders.put("item", item.displayName());
                placeholders.put("price", String.format("%,.2f", totalPrice));
                
                if (multiplier > 1.0) {
                    placeholders.put("multiplier", String.format("%.1fx", multiplier));
                    messageService.send(player, "shop.sell_success_with_multiplier", placeholders);
                } else {
                    messageService.send(player, "shop.sell_success", placeholders);
                }
                
                // Record transaction with multiplier price
                {
                    String shopId = null;
                    var pageInfo = shopGuiManager.getOpenPageInfo(player);
                    if (pageInfo != null) shopId = pageInfo.shopId();
                    if (shopId == null) shopId = "unknown";
                    shopGuiManager.recordRecentTransaction(player, shopId, item.displayName(), item.material().name(), quantity, totalPrice, "Sell", item.id());
                }
                successfulTransactions.incrementAndGet();
                return;
            }
            
            // Vault-based economy
            if (economy == null) {
                messageService.send(player, "shop.economy_not_available");
                failedTransactions.incrementAndGet();
                return;
            }
            if (item.sellPrice() <= 0) {
                messageService.send(player, "shop.sell_disabled");
                failedTransactions.incrementAndGet();
                return;
            }
            ItemStack toSell = new ItemStack(item.material(), quantity);
            if (!player.getInventory().containsAtLeast(toSell, quantity)) {
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("amount", String.valueOf(quantity));
                placeholders.put("item", item.displayName());
                messageService.send(player, "shop.item_not_owned", placeholders);
                failedTransactions.incrementAndGet();
                return;
            }
            player.getInventory().removeItem(toSell);
            
            // Apply multiplier to sell price
            double basePrice = item.sellPrice() * quantity;
            double multiplier = plugin.getMultiplierService().getPlayerMultiplier(player);
            double totalPrice = basePrice * multiplier;
            
            economy.depositPlayer(player, totalPrice);
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("amount", String.valueOf(quantity));
            placeholders.put("item", item.displayName());
            placeholders.put("price", String.format("%,.2f", totalPrice));
            
            if (multiplier > 1.0) {
                placeholders.put("multiplier", String.format("%.1fx", multiplier));
                messageService.send(player, "shop.sell_success_with_multiplier", placeholders);
            } else {
                messageService.send(player, "shop.sell_success", placeholders);
            }
            
            // Record transaction with multiplier price
            {
                String shopId = null;
                var pageInfo = shopGuiManager.getOpenPageInfo(player);
                if (pageInfo != null) shopId = pageInfo.shopId();
                if (shopId == null) shopId = "unknown";
                shopGuiManager.recordRecentTransaction(player, shopId, item.displayName(), item.material().name(), quantity, totalPrice, "Sell", item.id());
            }
            successfulTransactions.incrementAndGet();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during sell transaction for " + player.getName(), e);
            failedTransactions.incrementAndGet();
            messageService.send(player, "shop.transaction_error");
        }
    }

    public CompletableFuture<Boolean> sellItemAsync(Player player, ShopItem item, int quantity) {
        asyncTransactions.incrementAndGet();
        
        // Rate limiting
        if (enableRateLimiting) {
            long now = System.currentTimeMillis();
            long lastTime = lastTransactionTime.getOrDefault(player.getUniqueId(), 0L);
            if (now - lastTime < transactionCooldown) {
                return CompletableFuture.completedFuture(false);
            }
            lastTransactionTime.put(player.getUniqueId(), now);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                sellItem(player, item, quantity);
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Async sell transaction failed for " + player.getName(), e);
                failedTransactions.incrementAndGet();
                return false;
            }
        }, transactionExecutor).orTimeout(transactionTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (transactionExecutor != null && !transactionExecutor.isShutdown()) {
            transactionExecutor.shutdown();
            try {
                if (!transactionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    transactionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                transactionExecutor.shutdownNow();
            }
        }
        
        // Log final statistics
        long total = totalTransactions.get();
        long successful = successfulTransactions.get();
        long failed = failedTransactions.get();
        long async = asyncTransactions.get();
        
        plugin.getLogger().info(String.format("Transaction service shutdown - Total: %d, Successful: %d, Failed: %d, Async: %d", 
            total, successful, failed, async));
    }
    
    public void reloadConfig() {
        loadCooldownSettings();
    }
    
    /**
     * Get transaction statistics for monitoring
     */
    public Map<String, Object> getTransactionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_transactions", totalTransactions.get());
        stats.put("successful_transactions", successfulTransactions.get());
        stats.put("failed_transactions", failedTransactions.get());
        stats.put("async_transactions", asyncTransactions.get());
        stats.put("transaction_cooldown_ms", transactionCooldown);
        stats.put("enable_rate_limiting", enableRateLimiting);
        stats.put("max_concurrent_transactions", maxConcurrentTransactions);
        stats.put("transaction_timeout_ms", transactionTimeoutMs);
        stats.put("enable_retry_on_failure", enableRetryOnFailure);
        stats.put("max_retry_attempts", maxRetryAttempts);
        
        long total = totalTransactions.get();
        if (total > 0) {
            stats.put("success_rate_percent", (double) successfulTransactions.get() / total * 100);
            stats.put("async_rate_percent", (double) asyncTransactions.get() / total * 100);
        } else {
            stats.put("success_rate_percent", 0.0);
            stats.put("async_rate_percent", 0.0);
        }
        
        return stats;
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
package net.bumpier.bshop.shop.ui;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.model.PaginationItem;
import net.bumpier.bshop.shop.model.Shop;
import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.shop.transaction.TransactionContext;
import net.bumpier.bshop.shop.transaction.TransactionType;
import net.bumpier.bshop.util.ItemBuilder;
import net.bumpier.bshop.util.config.ConfigManager;
import net.bumpier.bshop.util.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.InventoryView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.inventory.meta.SkullMeta;
import java.lang.reflect.Field;

public class ShopGuiManager {

    private final BShop plugin;
    private final ShopManager shopManager;
    private final MessageService messageService;
    private final ConfigManager guisConfig;
    private final Map<UUID, PageInfo> openShopInventories = new ConcurrentHashMap<>();
    private final Map<UUID, TransactionContext> activeTransactions = new ConcurrentHashMap<>();

    // --- Recent Purchases Tracking ---
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, java.util.Deque<RecentTransaction>> recentTransactions = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_RECENT = 7;

    // --- Recent Purchases Pagination ---
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Integer> recentPurchasesPage = new java.util.concurrent.ConcurrentHashMap<>();

    public ShopGuiManager(BShop plugin, ShopManager shopManager, MessageService messageService, ConfigManager guisConfig) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.messageService = messageService;
        this.guisConfig = guisConfig;
    }

    // --- State Management ---
    public TransactionContext getTransactionContext(Player player) { return activeTransactions.get(player.getUniqueId()); }
    public PageInfo getOpenPageInfo(Player player) { return openShopInventories.get(player.getUniqueId()); }
    public void onGuiClose(Player player) {
        openShopInventories.remove(player.getUniqueId());
        // Only clear transaction context if player is not opening another BShop GUI
        // This prevents clearing the context during GUI transitions
    }
    
    public void clearTransactionContext(Player player) {
        activeTransactions.remove(player.getUniqueId());
    }

    public boolean isMainMenu(InventoryView view) {
        ConfigurationSection guiConfig = guisConfig.getConfig().getConfigurationSection("main-menu");
        if (guiConfig == null) return false;
        String configTitle = messageService.serialize(messageService.parse(guiConfig.getString("title", "")));
        String inventoryTitle = view.getTitle();
        return configTitle.equals(inventoryTitle);
    }

    public boolean isRecentPurchasesMenu(org.bukkit.inventory.InventoryView view) {
        ConfigurationSection guiConfig = guisConfig.getConfig().getConfigurationSection("recent-purchases-menu");
        if (guiConfig == null) return false;
        String configTitle = messageService.serialize(messageService.parse(guiConfig.getString("title", "")));
        String inventoryTitle = view.getTitle();
        return configTitle.equals(inventoryTitle);
    }

    // --- GUI Openers ---

    public void openMainMenu(Player player) {
        ConfigurationSection guiConfig = guisConfig.getConfig().getConfigurationSection("main-menu");
        if (guiConfig == null) {
            messageService.send(player, "gui.main_menu_not_configured");
            return;
        }
        String title = guiConfig.getString("title", "Shop");
        int size = guiConfig.getInt("size", 3) * 9;
        String serializedTitle = messageService.serialize(messageService.parse(title));
        Inventory inventory = Bukkit.createInventory(new BShopGUIHolder(), size, serializedTitle);

        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig != null) {
            for (String key : itemsConfig.getKeys(false)) {
                String path = key;
                Material material = Material.matchMaterial(itemsConfig.getString(path + ".material", "STONE"));
                if (material == null) continue;
                String base64 = itemsConfig.getString(path + ".base64-head");
                List<String> lore = itemsConfig.getStringList(path + ".lore");
                String displayName = itemsConfig.getString(path + ".display-name");
                int customModelData = itemsConfig.getInt(path + ".custom-model-data", 0);
                String action = itemsConfig.getString(path + ".action");
                ItemStack item;
                if (base64 != null && !base64.isEmpty() && material == Material.PLAYER_HEAD) {
                    item = ItemBuilder.createBase64Head(base64, displayName, lore, customModelData, messageService);
                } else {
                    item = new ItemBuilder(plugin, material, messageService)
                        .withDisplayName(displayName)
                        .withLore(lore)
                        .withCustomModelData(customModelData)
                        .build();
                }
                ItemMeta meta = item.getItemMeta();
                if (meta != null && action != null && !action.isEmpty()) {
                    NamespacedKey keyNS = new NamespacedKey(plugin, "bshop_action");
                    meta.getPersistentDataContainer().set(keyNS, PersistentDataType.STRING, action);
                    item.setItemMeta(meta);
                }
                inventory.setItem(itemsConfig.getInt(path + ".slot"), item);
            }
        }

        if (guiConfig.getBoolean("filler.enabled", false)) {
            Material fillerMat = Material.matchMaterial(guiConfig.getString("filler.material", "GRAY_STAINED_GLASS_PANE"));
            if (fillerMat != null) {
                ItemStack fillerStack = new ItemBuilder(plugin, fillerMat, messageService)
                        .withDisplayName(guiConfig.getString("filler.display-name", " ")).build();
                for (int i = 0; i < size; i++) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, fillerStack);
                    }
                }
            }
        }
        player.openInventory(inventory);
        ConfigurationSection walletConfig = guiConfig.getConfigurationSection("items.wallet");
        addWalletItem(inventory, walletConfig, player);
    }

    public void openShop(Player player, String shopId, int page) {
        Shop shop = shopManager.getShop(shopId);
        if (shop == null) {
            messageService.send(player, "shop.not_found", Placeholder.unparsed("shop", shopId));
            return;
        }
        // Load wallet config from /shops/{shopId}.yml
        ConfigManager shopConfig = new ConfigManager(plugin, "shops/" + shopId + ".yml");
        ConfigurationSection walletConfig = shopConfig.getConfig().getConfigurationSection("wallet");
        if (walletConfig == null) {
            // Fallback to global wallet config (main-menu)
            walletConfig = guisConfig.getConfig().getConfigurationSection("main-menu.items.wallet");
        }
        // If walletConfig is still null, just skip adding the wallet item
        String inventoryTitle = messageService.serialize(messageService.parse(shop.title()));
        Inventory inventory = Bukkit.createInventory(new BShopGUIHolder(), shop.size(), inventoryTitle);
        List<ShopItem> displayItems;
        if (shop.type() != null && shop.type().equalsIgnoreCase("rotational") && shop.activeItems() != null) {
            displayItems = shop.activeItems();
        } else {
            displayItems = shop.items();
        }
        // Use displayItems instead of shop.items() for flow and pinned logic
        List<Integer> reservedSlots = shop.paginationItems().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("filler"))
                .map(entry -> entry.getValue().slot())
                .collect(Collectors.toList());
        List<Integer> takenSlots = new ArrayList<>(reservedSlots);
        displayItems.stream().filter(ShopItem::isPinned).filter(item -> item.getPinnedPage().orElse(-1) == page)
                .forEach(item -> {
                    int slot = item.getPinnedSlot().get();
                    if (slot < shop.size() && !takenSlots.contains(slot)) {
                        item.setAssignedSlot(slot);
                        inventory.setItem(slot, createShopItemStack(item));
                        takenSlots.add(slot);
                    }
                });
        // Place featured items in featured slots for rotational shops
        if (shop.type() != null && shop.type().equalsIgnoreCase("rotational") && shop.featuredItems() != null && shop.featuredSlots() != null) {
            for (int i = 0; i < Math.min(shop.featuredItems().size(), shop.featuredSlots().size()); i++) {
                ShopItem featured = shop.featuredItems().get(i);
                int slot = shop.featuredSlots().get(i);
                featured.setAssignedSlot(slot);
                inventory.setItem(slot, createShopItemStack(featured));
                takenSlots.add(slot);
            }
        }
        List<Integer> availableSlots;
        if (shop.type() != null && shop.type().equalsIgnoreCase("rotational") && shop.itemSlots() != null && !shop.itemSlots().isEmpty()) {
            availableSlots = new ArrayList<>(shop.itemSlots());
        } else {
            availableSlots = new ArrayList<>();
            for (int i = 0; i < shop.size(); i++) {
                if (!takenSlots.contains(i)) availableSlots.add(i);
            }
        }
        List<ShopItem> flowItems = displayItems.stream().filter(item -> !item.isPinned()).collect(Collectors.toList());
        int itemsPerPage = availableSlots.size();
        int totalFlowPages = itemsPerPage > 0 ? (int) Math.ceil((double) flowItems.size() / itemsPerPage) : 0;
        int startIndex = page * itemsPerPage;
        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < flowItems.size()) {
                ShopItem shopItem = flowItems.get(itemIndex);
                int slot = availableSlots.get(i);
                shopItem.setAssignedSlot(slot);
                inventory.setItem(slot, createShopItemStack(shopItem));
            }
        }
        int maxPinnedPage = displayItems.stream().filter(ShopItem::isPinned)
                .mapToInt(item -> item.getPinnedPage().orElse(0)).max().orElse(0);
        int totalPages = Math.max(maxPinnedPage + 1, totalFlowPages);

        // --- Only show prev/next if there are items on those pages ---
        // Check for items on previous page
        boolean hasPrev = false;
        if (page > 0) {
            boolean prevHasPinned = displayItems.stream().anyMatch(item -> item.isPinned() && item.getPinnedPage().orElse(-1) == (page - 1));
            boolean prevHasFlow = false;
            if (itemsPerPage > 0) {
                int prevStart = (page - 1) * itemsPerPage;
                prevHasFlow = prevStart < flowItems.size();
            }
            hasPrev = prevHasPinned || prevHasFlow;
        }
        if (hasPrev) {
            PaginationItem prevButton = shop.paginationItems().get("previous_page");
            if (prevButton != null) inventory.setItem(prevButton.slot(), createPaginationItemStack(prevButton));
        }

        // Check for items on next page
        boolean hasNext = false;
        if (page < totalPages - 1) {
            boolean nextHasPinned = displayItems.stream().anyMatch(item -> item.isPinned() && item.getPinnedPage().orElse(-1) == (page + 1));
            boolean nextHasFlow = false;
            if (itemsPerPage > 0) {
                int nextStart = (page + 1) * itemsPerPage;
                nextHasFlow = nextStart < flowItems.size();
            }
            hasNext = nextHasPinned || nextHasFlow;
        }
        if (hasNext) {
            PaginationItem nextButton = shop.paginationItems().get("next_page");
            if (nextButton != null) inventory.setItem(nextButton.slot(), createPaginationItemStack(nextButton));
        }
        
        // Add back to main menu button
        PaginationItem backButton = shop.paginationItems().get("back_to_menu");
        if (backButton != null) {
            inventory.setItem(backButton.slot(), createPaginationItemStack(backButton));
        }
        
        // Add filler items for shops
        PaginationItem fillerItem = shop.paginationItems().get("filler");
        if (fillerItem != null) {
            ItemStack fillerStack = createPaginationItemStack(fillerItem);
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, fillerStack);
                }
            }
        }
        
        player.openInventory(inventory);
        openShopInventories.put(player.getUniqueId(), new PageInfo(shopId, page));
        if (walletConfig != null) {
            addWalletItem(inventory, walletConfig, player);
        }
    }

    public void openQuantityGui(Player player, ShopItem item, TransactionType type) {
        TransactionContext context = new TransactionContext(item, type);
        openQuantityGui(player, context);
    }
    
    public void openQuantityGui(Player player, ShopItem item, TransactionType type, String sourceShopId, int sourceShopPage) {
        TransactionContext context = new TransactionContext(item, type, sourceShopId, sourceShopPage);
        openQuantityGui(player, context);
    }
    
    public void openQuantityGui(Player player, TransactionContext context) {
        activeTransactions.put(player.getUniqueId(), context);
        ConfigurationSection config = guisConfig.getConfig().getConfigurationSection("quantity-menu");
        if (config == null) {
            messageService.send(player, "gui.quantity_menu_not_configured");
            return;
        }
        try {
            String title = messageService.serialize(messageService.parse(config.getString("title", "Select Quantity")));
            Inventory inventory = Bukkit.createInventory(new BShopGUIHolder(), config.getInt("size", 4) * 9, title);
            updateQuantityGui(inventory, context);
            player.openInventory(inventory);
        } catch (Exception e) {
            messageService.send(player, "gui.error_opening_quantity_gui", Placeholder.unparsed("error", e.getMessage()));
        }
    }

    public void openStackGui(Player player, TransactionContext context) {
        ConfigurationSection config = guisConfig.getConfig().getConfigurationSection("quantity-menu.stack_gui");
        if (config == null) {
            messageService.send(player, "gui.stack_gui_not_configured");
            return;
        }
        
        String title = messageService.serialize(messageService.parse(config.getString("title", "Select Stacks")));
        int size = config.getInt("size", 2) * 9; // Size in rows, convert to slots
        Inventory inventory = Bukkit.createInventory(new BShopGUIHolder(), size, title);
        
        ShopItem shopItem = context.getItem();
        TransactionType type = context.getType();
        double pricePerItem = (type == TransactionType.BUY) ? shopItem.buyPrice() : shopItem.sellPrice();
        int stackSize = shopItem.material().getMaxStackSize();
        
        // Add configurable stack items
        ConfigurationSection itemsConfig = config.getConfigurationSection("items");
        if (itemsConfig != null) {
            for (String key : itemsConfig.getKeys(false)) {
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(key);
                if (itemConfig == null) continue;
                
                Material material = Material.matchMaterial(itemConfig.getString("material", "LIGHT_BLUE_STAINED_GLASS_PANE"));
                if (material == null) continue;
                
                int amount = itemConfig.getInt("amount", stackSize); // Default to 1 stack worth
                int slot = itemConfig.getInt("slot", 0);
                String action = itemConfig.getString("action", "set_quantity_amount:" + amount);
                
                int totalAmount = amount;
                double totalPrice = pricePerItem * totalAmount;
                
                // Calculate stacks for display purposes
                double stacks = (double) amount / stackSize;
                
                // Get display name based on transaction type
                String displayName = (type == TransactionType.BUY) ? 
                    itemConfig.getString("display-name", "<aqua>Buy {stacks} Stack(s)") : 
                    itemConfig.getString("sell_display-name", itemConfig.getString("display-name", "<red>Sell {stacks} Stack(s)"));
                
                // Process placeholders in display name
                String stacksDisplay = stacks == (int) stacks ? String.valueOf((int) stacks) : String.format("%.1f", stacks);
                displayName = displayName.replace("%stacks%", stacksDisplay)
                        .replace("%amount%", String.valueOf(totalAmount))
                        .replace("%price%", String.format("%,.2f", totalPrice))
                        .replace("%item_name%", shopItem.displayName());
                
                // Process placeholders in lore
                List<String> lore = itemConfig.getStringList("lore").stream()
                        .map(line -> line.replace("%stacks%", stacksDisplay)
                                .replace("%amount%", String.valueOf(totalAmount))
                                .replace("%price%", String.format("%,.2f", totalPrice))
                                .replace("%item_name%", shopItem.displayName()))
                        .collect(Collectors.toList());
                
                ItemStack stackItem = new ItemBuilder(plugin, material, messageService)
                        .withDisplayName(displayName)
                        .withLore(lore)
                        .withAmount(Math.min(amount, 64)) // Set the visual amount (clamped to max stack size)
                        .withPDCString("bshop_action", action)
                        .build();
                
                inventory.setItem(slot, stackItem);
            }
        }
        
        // Add configurable back button
        ConfigurationSection backButtonConfig = config.getConfigurationSection("back_button");
        if (backButtonConfig != null) {
            Material backMaterial = Material.matchMaterial(backButtonConfig.getString("material", "REDSTONE"));
            if (backMaterial != null) {
                String backName = backButtonConfig.getString("display-name", "<red>Go Back");
                List<String> backLore = backButtonConfig.getStringList("lore");
                int backSlot = backButtonConfig.getInt("slot", 13);
                String backAction = backButtonConfig.getString("action", "open_quantity_menu");
                
                ItemStack backButton = new ItemBuilder(plugin, backMaterial, messageService)
                        .withDisplayName(backName)
                        .withLore(backLore)
                        .withPDCString("bshop_action", backAction)
                        .build();
                inventory.setItem(backSlot, backButton);
            }
        }
        
        // Add filler items to stack GUI
        ConfigurationSection fillerConfig = config.getConfigurationSection("filler");
        if (fillerConfig != null && fillerConfig.getBoolean("enabled", false)) {
            Material fillerMaterial = Material.matchMaterial(fillerConfig.getString("material", "BLACK_STAINED_GLASS_PANE"));
            if (fillerMaterial != null) {
                String fillerName = fillerConfig.getString("display-name", " ");
                List<String> fillerLore = fillerConfig.getStringList("lore");
                
                ItemStack fillerItem = new ItemBuilder(plugin, fillerMaterial, messageService)
                        .withDisplayName(fillerName)
                        .withLore(fillerLore)
                        .build();
                
                // Fill all empty slots
                for (int i = 0; i < inventory.getSize(); i++) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, fillerItem);
                    }
                }
            }
        }
        
        player.openInventory(inventory);
    }

    public void updateQuantityGui(Inventory inventory, TransactionContext context) {
        inventory.clear();
        ConfigurationSection config = guisConfig.getConfig().getConfigurationSection("quantity-menu");
        if (config == null) return;
        ShopItem item = context.getItem();
        TransactionType type = context.getType();
        int quantity = context.getQuantity();
        double pricePerItem = (type == TransactionType.BUY) ? item.buyPrice() : item.sellPrice();
        double totalPrice = pricePerItem * quantity;
        
        // Get display item configuration
        ConfigurationSection displayItemConfig = config.getConfigurationSection("display_item");
        String displayName;
        List<String> lore;
        
        if (displayItemConfig != null) {
            // Use configured display item
            displayName = (type == TransactionType.BUY) ? 
                displayItemConfig.getString("buy_display-name", "<green>Buying: %item_name%") : 
                displayItemConfig.getString("sell_display-name", "<red>Selling: %item_name%");
            lore = displayItemConfig.getStringList("lore");
        } else {
            // Fallback to default format
            displayName = (type == TransactionType.BUY) ? "<green>Buying: %item_name%" : "<red>Selling: %item_name%";
            lore = List.of(
                "<gray>Quantity: <yellow>%quantity%</yellow>",
                "<gray>Price per item: <gold>%price_per_item%</gold>",
                "<gray>Total Price: <gold>%total_price%</gold>"
            );
        }
        
        // Replace placeholders
        displayName = displayName.replace("%item_name%", item.displayName()).replace("%item%", item.displayName());
        List<String> processedLore = lore.stream()
                .map(line -> line.replace("%quantity%", String.valueOf(quantity))
                        .replace("%price_per_item%", String.format("%,.2f", pricePerItem))
                        .replace("%total_price%", String.format("%,.2f", totalPrice))
                        .replace("%item_name%", item.displayName())
                        .replace("%item%", item.displayName()))
                .collect(Collectors.toList());
        
        ItemStack displayItem = new ItemBuilder(plugin, item.material(), messageService)
                .withDisplayName(displayName)
                .withLore(processedLore)
                .build();
        inventory.setItem(config.getInt("display_item_slot", 13), displayItem);
        ConfigurationSection buttons = config.getConfigurationSection("buttons");
        if (buttons == null) return;
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection buttonConfig = buttons.getConfigurationSection(key);
            if (buttonConfig == null) continue;
            
            int slot = buttonConfig.getInt("slot");
            String action = buttonConfig.getString("action");
            List<String> buttonLore = buttonConfig.getStringList("lore");
            String buttonName;
            Material material;
            if (key.equals("confirm")) {
                buttonName = (type == TransactionType.BUY) ? buttonConfig.getString("buy_display-name", buttonConfig.getString("display-name")) : buttonConfig.getString("sell_display-name", buttonConfig.getString("display-name"));
                material = (type == TransactionType.BUY) ? Material.matchMaterial(buttonConfig.getString("buy_material")) : Material.matchMaterial(buttonConfig.getString("sell_material"));
            } else {
                buttonName = buttonConfig.getString("display-name");
                material = Material.matchMaterial(buttonConfig.getString("material"));
            }
            if (material == null) continue;
            ItemStack buttonItem = new ItemBuilder(plugin, material, messageService)
                    .withDisplayName(buttonName).withLore(buttonLore).withPDCString("bshop_action", action).build();
            inventory.setItem(slot, buttonItem);
        }
        
        // Add filler items
        ConfigurationSection fillerConfig = config.getConfigurationSection("filler");
        if (fillerConfig != null && fillerConfig.getBoolean("enabled", false)) {
            Material fillerMaterial = Material.matchMaterial(fillerConfig.getString("material", "GRAY_STAINED_GLASS_PANE"));
            if (fillerMaterial != null) {
                String fillerName = fillerConfig.getString("display-name", " ");
                List<String> fillerLore = fillerConfig.getStringList("lore");
                
                ItemStack fillerItem = new ItemBuilder(plugin, fillerMaterial, messageService)
                        .withDisplayName(fillerName)
                        .withLore(fillerLore)
                        .build();
                
                // Fill all empty slots
                for (int i = 0; i < inventory.getSize(); i++) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, fillerItem);
                    }
                }
            }
        }
    }

    private String formatTimer(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%02dm %02ds", minutes, secs);
        } else {
            return String.format("%02ds", secs);
        }
    }

    private ItemStack createShopItemStack(ShopItem shopItem) {
        // Build the item stack using the ItemBuilder constructor and chain methods
        ItemBuilder builder = new ItemBuilder(plugin, shopItem.material(), messageService)
                .withDisplayName(shopItem.displayName())
                .withLore(shopItem.lore())
                .withCustomModelData(shopItem.customModelData());
        ItemStack itemStack = builder.build();
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                List<String> newLore = new ArrayList<>();
                for (String line : lore) {
                    // Replace price placeholders
                    String processedLine = line
                            .replace("%buy_price%", String.format("%,.2f", shopItem.buyPrice()))
                            .replace("%sell_price%", String.format("%,.2f", shopItem.sellPrice()));
                    
                    // Replace timer placeholder for rotational shops
                    String shopId = null;
                    for (Map.Entry<UUID, PageInfo> entry : openShopInventories.entrySet()) {
                        if (entry.getValue().shopId() != null) {
                            shopId = entry.getValue().shopId();
                            break;
                        }
                    }
                    if (shopId != null) {
                        long timeLeft = shopManager.getTimeUntilNextRotation(shopId);
                        String timer = formatTimer(timeLeft);
                        processedLine = processedLine.replace("%timer%", timer);
                    }
                    
                    newLore.add(processedLine);
                }
                meta.setLore(newLore);
            }
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public ItemStack createPaginationItemStack(PaginationItem paginationItem) {
        return new ItemBuilder(plugin, paginationItem.material(), messageService)
                .withDisplayName(paginationItem.displayName()).withLore(paginationItem.lore()).build();
    }

    public void openRecentPurchasesMenu(Player player) {
        openRecentPurchasesMenu(player, 0);
    }

    public void openRecentPurchasesMenu(Player player, int page) {
        ConfigurationSection guiConfig = guisConfig.getConfig().getConfigurationSection("recent-purchases-menu");
        if (guiConfig == null) {
            messageService.send(player, "gui.recent_purchases_not_configured");
            return;
        }
        String title = guiConfig.getString("title", "Recent Purchases");
        int size = guiConfig.getInt("size", 3) * 9;
        String serializedTitle = messageService.serialize(messageService.parse(title));
        Inventory inventory = Bukkit.createInventory(new BShopGUIHolder(), size, serializedTitle);

        // --- Retrieve recent purchases ---
        List<RecentTransaction> allRecent = getRecentTransactionsForPlayer(player);
        int itemsPerPage = 7;
        int totalPages = (int) Math.ceil((double) allRecent.size() / itemsPerPage);
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);
        recentPurchasesPage.put(player.getUniqueId(), page);
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allRecent.size());
        List<RecentTransaction> recent = allRecent.subList(start, end);

        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig != null) {
            int txIndex = 0;
            for (String key : itemsConfig.getKeys(false)) {
                ConfigurationSection itemCfg = itemsConfig.getConfigurationSection(key);
                if (itemCfg == null) continue;
                int slot = itemCfg.getInt("slot");
                String displayName = itemCfg.getString("display-name", "");
                String materialStr = itemCfg.getString("material", "STONE");
                List<String> lore = itemCfg.getStringList("lore");
                String action = itemCfg.getString("action");
                Material material = Material.matchMaterial(materialStr);
                if (key.startsWith("purchase_") && txIndex < recent.size()) {
                    RecentTransaction tx = recent.get(txIndex++);
                    displayName = replaceStandardPlaceholders(displayName, tx);
                    material = Material.matchMaterial(tx.material);
                    List<String> processedLore = replaceStandardPlaceholders(lore, tx);
                    ItemStack item = new ItemBuilder(plugin, material != null ? material : Material.STONE, messageService)
                            .withDisplayName(displayName)
                            .withLore(processedLore)
                            .build();
                    inventory.setItem(slot, item);
                } else if ("back".equals(key)) {
                    ItemStack item = new ItemBuilder(plugin, material != null ? material : Material.BARRIER, messageService)
                            .withDisplayName(displayName)
                            .withLore(lore)
                            .build();
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && action != null && !action.isEmpty()) {
                        NamespacedKey keyNS = new NamespacedKey(plugin, "bshop_action");
                        meta.getPersistentDataContainer().set(keyNS, org.bukkit.persistence.PersistentDataType.STRING, "back_to_main");
                        item.setItemMeta(meta);
                    }
                    inventory.setItem(slot, item);
                } else if ("next_page".equals(key) && page < totalPages - 1) {
                    ItemStack item = new ItemBuilder(plugin, material != null ? material : Material.ARROW, messageService)
                            .withDisplayName(displayName)
                            .withLore(lore)
                            .build();
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && action != null && !action.isEmpty()) {
                        NamespacedKey keyNS = new NamespacedKey(plugin, "bshop_action");
                        meta.getPersistentDataContainer().set(keyNS, org.bukkit.persistence.PersistentDataType.STRING, action);
                        item.setItemMeta(meta);
                    }
                    inventory.setItem(slot, item);
                } else if ("previous_page".equals(key) && page > 0) {
                    ItemStack item = new ItemBuilder(plugin, material != null ? material : Material.ARROW, messageService)
                            .withDisplayName(displayName)
                            .withLore(lore)
                            .build();
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && action != null && !action.isEmpty()) {
                        NamespacedKey keyNS = new NamespacedKey(plugin, "bshop_action");
                        meta.getPersistentDataContainer().set(keyNS, org.bukkit.persistence.PersistentDataType.STRING, action);
                        item.setItemMeta(meta);
                    }
                    inventory.setItem(slot, item);
                }
            }
        }
        // Filler
        ConfigurationSection fillerConfig = guiConfig.getConfigurationSection("filler");
        if (fillerConfig != null && fillerConfig.getBoolean("enabled", false)) {
            Material fillerMat = Material.matchMaterial(fillerConfig.getString("material", "GRAY_STAINED_GLASS_PANE"));
            if (fillerMat != null) {
                ItemStack fillerStack = new ItemBuilder(plugin, fillerMat, messageService)
                        .withDisplayName(fillerConfig.getString("display-name", " ")).build();
                for (int i = 0; i < size; i++) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, fillerStack);
                    }
                }
            }
        }
        player.openInventory(inventory);
        ConfigurationSection walletConfig = guiConfig.getConfigurationSection("items.wallet");
        addWalletItem(inventory, walletConfig, player);
    }

    public void handleRecentPurchasesPageAction(Player player, String action) {
        int page = recentPurchasesPage.getOrDefault(player.getUniqueId(), 0);
        if ("recent_purchases_next".equals(action)) {
            openRecentPurchasesMenu(player, page + 1);
        } else if ("recent_purchases_prev".equals(action)) {
            openRecentPurchasesMenu(player, page - 1);
        }
    }

    // Temporary stub for recent transactions
    public List<RecentTransaction> getRecentTransactionsForPlayer(Player player) {
        java.util.Deque<RecentTransaction> deque = recentTransactions.get(player.getUniqueId());
        if (deque == null) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(deque);
    }

    public void recordRecentTransaction(Player player, String shopId, String itemName, String material, int amount, double price, String type, String itemId) {
        String shopName = "N/A";
        String transactionId = java.util.UUID.randomUUID().toString().substring(0, 8);
        double balanceAfter = 0.0;
        if (player != null && player.isOnline()) {
            balanceAfter = plugin.getEconomy().getBalance(player);
        }
        java.util.Deque<RecentTransaction> deque = recentTransactions.computeIfAbsent(player.getUniqueId(), k -> new java.util.LinkedList<>());
        String date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.LocalDateTime.now());
        deque.addFirst(new RecentTransaction(itemName, material, amount, price, type, date, shopName, transactionId, balanceAfter, itemId));
        while (deque.size() > MAX_RECENT) deque.removeLast();
        // Log to file
        net.bumpier.bshop.shop.ui.ShopTransactionLogger.logTransaction(
            player.getUniqueId().toString(), player.getName(), shopId, itemName, material, amount, price, type, balanceAfter, transactionId, date, itemId
        );
    }

    // Update RecentTransaction class
    private static class RecentTransaction {
        public String itemName;
        public String material;
        public int amount;
        public double price;
        public String type; // "Buy" or "Sell"
        public String date;
        public String shopName;
        public String transactionId;
        public double balanceAfter;
        public String itemId;
        public RecentTransaction(String itemName, String material, int amount, double price, String type, String date, String shopName, String transactionId, double balanceAfter, String itemId) {
            this.itemName = itemName;
            this.material = material;
            this.amount = amount;
            this.price = price;
            this.type = type;
            this.date = date;
            this.shopName = shopName;
            this.transactionId = transactionId;
            this.balanceAfter = balanceAfter;
            this.itemId = itemId;
        }
    }

    // Utility to replace standard placeholders in a string (now only supports %placeholder% style)
    private String replaceStandardPlaceholders(String text, RecentTransaction tx) {
        return text.replace("%item%", tx.itemName)
                   .replace("%amount%", String.valueOf(tx.amount))
                   .replace("%price%", String.format("%,.2f", tx.price))
                   .replace("%type%", tx.type)
                   .replace("%date%", tx.date)
                   .replace("%shop%", tx.shopName)
                   .replace("%transaction_id%", tx.transactionId)
                   .replace("%balance_after%", String.format("%,.2f", tx.balanceAfter))
                   .replace("%item_id%", tx.itemId);
    }
    private List<String> replaceStandardPlaceholders(List<String> lines, RecentTransaction tx) {
        List<String> out = new java.util.ArrayList<>();
        for (String line : lines) out.add(replaceStandardPlaceholders(line, tx));
        return out;
    }

    // Helper to add wallet item to a GUI
    private void addWalletItem(Inventory inventory, ConfigurationSection walletConfig, Player player) {
        if (walletConfig == null) return;
        Material material = Material.matchMaterial(walletConfig.getString("material", "GOLD_INGOT"));
        int slot = walletConfig.getInt("slot", 4);
        String displayName = walletConfig.getString("display-name", "<gold><bold>Wallet</bold>");
        List<String> lore = walletConfig.getStringList("lore");
        String action = walletConfig.getString("action", "wallet");
        // Check for PlaceholderAPI
        boolean papiPresent = false;
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiPresent = org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        } catch (ClassNotFoundException ignored) {}
        if (papiPresent) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = papi.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class);
                displayName = (String) setPlaceholders.invoke(null, player, displayName);
                List<String> parsedLore = new java.util.ArrayList<>();
                for (String line : lore) {
                    parsedLore.add((String) setPlaceholders.invoke(null, player, line));
                }
                lore = parsedLore;
            } catch (Exception ignored) {}
        }
        ItemStack item = new ItemBuilder(plugin, material != null ? material : Material.GOLD_INGOT, messageService)
                .withDisplayName(displayName)
                .withLore(lore)
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && action != null && !action.isEmpty()) {
            NamespacedKey keyNS = new NamespacedKey(plugin, "bshop_action");
            meta.getPersistentDataContainer().set(keyNS, org.bukkit.persistence.PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    public ShopManager getShopManager() { return shopManager; }
}
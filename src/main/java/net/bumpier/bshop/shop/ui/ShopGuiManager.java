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

public class ShopGuiManager {

    private final BShop plugin;
    private final ShopManager shopManager;
    private final MessageService messageService;
    private final ConfigManager guisConfig;
    private final Map<UUID, PageInfo> openShopInventories = new ConcurrentHashMap<>();
    private final Map<UUID, TransactionContext> activeTransactions = new ConcurrentHashMap<>();

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
                ItemBuilder builder = new ItemBuilder(plugin, material, messageService)
                        .withDisplayName(itemsConfig.getString(path + ".display-name"))
                        .withLore(itemsConfig.getStringList(path + ".lore"))
                        .withCustomModelData(itemsConfig.getInt(path + ".custom-model-data", 0));
                String action = itemsConfig.getString(path + ".action");
                builder.withPDCString("bshop_action", action);
                inventory.setItem(itemsConfig.getInt(path + ".slot"), builder.build());
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
    }

    public void openShop(Player player, String shopId, int page) {
        Shop shop = shopManager.getShop(shopId);
        if (shop == null) {
            messageService.send(player, "shop.not_found", Placeholder.unparsed("id", shopId));
            return;
        }
        String inventoryTitle = messageService.serialize(messageService.parse(shop.title()));
        Inventory inventory = Bukkit.createInventory(new BShopGUIHolder(), shop.size(), inventoryTitle);
        List<Integer> reservedSlots = shop.paginationItems().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("filler")) // Exclude filler as it doesn't have a specific slot
                .map(entry -> entry.getValue().slot())
                .collect(Collectors.toList());
        List<Integer> takenSlots = new ArrayList<>(reservedSlots);
        shop.items().stream().filter(ShopItem::isPinned).filter(item -> item.getPinnedPage().orElse(-1) == page)
                .forEach(item -> {
                    int slot = item.getPinnedSlot().get();
                    if (slot < shop.size() && !takenSlots.contains(slot)) {
                        item.setAssignedSlot(slot);
                        inventory.setItem(slot, createShopItemStack(item));
                        takenSlots.add(slot);
                    }
                });
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < shop.size(); i++) {
            if (!takenSlots.contains(i)) availableSlots.add(i);
        }
        List<ShopItem> flowItems = shop.items().stream().filter(item -> !item.isPinned()).collect(Collectors.toList());
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
        int maxPinnedPage = shop.items().stream().filter(ShopItem::isPinned)
                .mapToInt(item -> item.getPinnedPage().orElse(0)).max().orElse(0);
        int totalPages = Math.max(maxPinnedPage + 1, totalFlowPages);
        if (page > 0) {
            PaginationItem prevButton = shop.paginationItems().get("previous_page");
            if (prevButton != null) inventory.setItem(prevButton.slot(), createPaginationItemStack(prevButton));
        }
        if (page < totalPages - 1) {
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
        Material itemMaterial = Material.matchMaterial(config.getString("item.material", "PAPER"));
        for (int i = 1; i <= 9; i++) {
            int totalAmount = stackSize * i;
            double totalPrice = pricePerItem * totalAmount;
            String nameKey = (type == TransactionType.BUY) ? "item.buy_display-name" : "item.sell_display-name";
            String name = config.getString(nameKey, "<aqua>Buy {stacks} Stack(s)")
                    .replace("{stacks}", String.valueOf(i));
            List<String> lore = config.getStringList("item.lore").stream()
                    .map(line -> line.replace("{amount}", String.valueOf(totalAmount))
                            .replace("{price}", String.format("%,.2f", totalPrice)))
                    .collect(Collectors.toList());
            ItemStack stackSelectItem = new ItemBuilder(plugin, itemMaterial, messageService)
                    .withDisplayName(name).withLore(lore).withPDCString("bshop_action", "set_quantity_stacks:" + i).build();
            inventory.setItem(i - 1, stackSelectItem);
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
                
                // Fill all empty slots (avoid slots 0-8 for stack buttons and slot 13 for back button)
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
                displayItemConfig.getString("buy_display-name", "<green>Buying: {item_name}") : 
                displayItemConfig.getString("sell_display-name", "<red>Selling: {item_name}");
            lore = displayItemConfig.getStringList("lore");
        } else {
            // Fallback to default format
            displayName = (type == TransactionType.BUY) ? "<green>Buying: {item_name}" : "<red>Selling: {item_name}";
            lore = List.of(
                "<gray>Quantity: <yellow>{quantity}</yellow>",
                "<gray>Price per item: <gold>${price_per_item}</gold>",
                "<gray>Total Price: <gold>${total_price}</gold>"
            );
        }
        
        // Replace placeholders
        displayName = displayName.replace("{item_name}", item.displayName());
        List<String> processedLore = lore.stream()
                .map(line -> line.replace("{quantity}", String.valueOf(quantity))
                        .replace("{price_per_item}", String.format("%,.2f", pricePerItem))
                        .replace("{total_price}", String.format("%,.2f", totalPrice))
                        .replace("{item_name}", item.displayName()))
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

    private ItemStack createShopItemStack(ShopItem shopItem) {
        List<String> formattedLore = shopItem.lore().stream()
                .map(line -> line.replace("${buy_price}", String.format("%,.2f", shopItem.buyPrice()))
                        .replace("${sell_price}", String.format("%,.2f", shopItem.sellPrice())))
                .collect(Collectors.toList());
        return new ItemBuilder(plugin, shopItem.material(), messageService)
                .withDisplayName(shopItem.displayName()).withLore(formattedLore)
                .withCustomModelData(shopItem.customModelData()).build();
    }

    private ItemStack createPaginationItemStack(PaginationItem paginationItem) {
        return new ItemBuilder(plugin, paginationItem.material(), messageService)
                .withDisplayName(paginationItem.displayName()).withLore(paginationItem.lore()).build();
    }
}
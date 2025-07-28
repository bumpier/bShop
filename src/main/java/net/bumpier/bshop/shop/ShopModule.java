package net.bumpier.bshop.shop;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.module.Module;
import net.bumpier.bshop.module.ModuleManager; // Import ModuleManager
import net.bumpier.bshop.command.ShopCommand;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.shop.ui.ShopListener;
import net.bumpier.bshop.util.config.ConfigManager;
import net.bumpier.bshop.util.message.MessageService;

public class ShopModule implements Module {

    private final BShop plugin;
    private final ModuleManager moduleManager; // Store ModuleManager
    public static net.bumpier.bshop.util.message.MessageService globalMessageService;

    public ShopModule(BShop plugin, ModuleManager moduleManager) { // Accept ModuleManager
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    @Override
    public void onEnable() {
        // --- Centralized Service & Config Creation ---
        ConfigManager messagesConfig = new ConfigManager(plugin, "messages.yml");
        ConfigManager guisConfig = new ConfigManager(plugin, "guis.yml");

        MessageService messageService = new MessageService(plugin, messagesConfig);
        globalMessageService = messageService;
        ShopManager shopManager = new ShopManager(plugin);
        shopManager.startRotationTask(plugin);
        ShopGuiManager shopGuiManager = new ShopGuiManager(plugin, shopManager, messageService, guisConfig);
        ShopTransactionService transactionService = new ShopTransactionService(plugin, messageService, shopGuiManager);

        // --- Register Commands and Listeners ---
        // Inject the ModuleManager into the command handler
        plugin.getCommand("shop").setExecutor(new ShopCommand(shopGuiManager, messageService, shopManager, moduleManager));
        plugin.getServer().getPluginManager().registerEvents(new ShopListener(shopGuiManager, transactionService, shopManager, messageService), plugin);
    }

    @Override
    public void onDisable() {
        // No specific logic needed here for now, as services are self-contained.
    }

    @Override
    public String getName() {
        return "ShopModule";
    }
}
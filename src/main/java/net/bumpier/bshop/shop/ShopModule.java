package net.bumpier.bshop.shop;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.module.Module;
import net.bumpier.bshop.module.ModuleManager;
import net.bumpier.bshop.command.ShopCommand;
import net.bumpier.bshop.shop.transaction.ShopTransactionService;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.shop.ui.ShopListener;
import net.bumpier.bshop.util.message.MessageService;

public class ShopModule implements Module {

    private final BShop plugin;
    private final ModuleManager moduleManager;
    public static net.bumpier.bshop.util.message.MessageService globalMessageService;

    public ShopModule(BShop plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    @Override
    public void onEnable() {
        // Use services already created in the main BShop class
        MessageService messageService = plugin.getMessageService();
        globalMessageService = messageService;
        ShopManager shopManager = plugin.getShopManager();
        ShopGuiManager shopGuiManager = plugin.getShopGuiManager();
        ShopTransactionService transactionService = plugin.getTransactionService();

        // Register Commands and Listeners
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
package net.bumpier.bshop.command;

import net.bumpier.bshop.module.ModuleManager;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.util.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ShopCommand implements CommandExecutor {

    private final ShopGuiManager shopGuiManager;
    private final MessageService messageService;
    private final ShopManager shopManager;
    private final ModuleManager moduleManager;

    public ShopCommand(ShopGuiManager shopGuiManager, MessageService messageService, ShopManager shopManager, ModuleManager moduleManager) {
        this.shopGuiManager = shopGuiManager;
        this.messageService = messageService;
        this.shopManager = shopManager;
        this.moduleManager = moduleManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                messageService.send(sender, "player_only_command");
                return true;
            }
            shopGuiManager.openMainMenu((Player) sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("reload")) {
            handleReload(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            messageService.send(sender, "player_only_command");
            return true;
        }
        handleOpenCategory((Player) sender, subCommand);
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("bshop.admin.reload")) {
            messageService.send(sender, "no_permission");
            return;
        }

        // Use the configurable messages from messages.yml
        messageService.send(sender, "admin.reload_start");
        moduleManager.reloadModules();
        messageService.send(sender, "admin.reload_complete");
    }

    private void handleOpenCategory(Player player, String shopId) {
        if (shopManager.getShop(shopId) == null) {
            messageService.send(player, "shop.not_found", Placeholder.unparsed("id", shopId));
            return;
        }
        shopGuiManager.openShop(player, shopId, 0);
    }
}
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

        if (subCommand.equals("rotate")) {
            handleRotate(sender, args);
            return true;
        }

        if (subCommand.equals("view")) {
            handleView(sender, args);
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
        
        messageService.send(sender, "admin.reload_start");
        
        // Reload main config
        net.bumpier.bshop.BShop.getInstance().reloadConfig();
        
        // Reload messages configuration
        messageService.reloadConfig();
        
        // Reload GUIs configuration
        shopGuiManager.reloadConfig();
        
        // Reload shops
        shopManager.loadShops();
        
        // Reload modules
        moduleManager.reloadModules();
        
        messageService.send(sender, "admin.reload_complete");
        if (sender instanceof Player) {
            shopGuiManager.openMainMenu((Player) sender);
        }
    }

    private void handleOpenCategory(Player player, String shopId) {
        if (shopManager.getShop(shopId) == null) {
            messageService.send(player, "shop.not_found", Placeholder.unparsed("shop", shopId));
            return;
        }
        shopGuiManager.openShop(player, shopId, 0);
    }

    private void handleRotate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bshop.admin.rotate")) {
            messageService.send(sender, "no_permission");
            return;
        }
        if (args.length < 2) {
            messageService.send(sender, "admin.rotate_usage");
            return;
        }
        String shopId = args[1].toLowerCase();
        var shop = shopManager.getShop(shopId);
        if (shop == null || shop.type() == null || !shop.type().equalsIgnoreCase("rotational")) {
            messageService.send(sender, "admin.rotate_not_rotational", Placeholder.unparsed("shop", shopId));
            return;
        }
        // Rotate items immediately
        java.util.List<net.bumpier.bshop.shop.model.ShopItem> shuffled = new java.util.ArrayList<>(shop.items());
        java.util.Collections.shuffle(shuffled);
        java.util.List<net.bumpier.bshop.shop.model.ShopItem> newActive = new java.util.ArrayList<>(shuffled.subList(0, Math.min(shop.slots(), shuffled.size())));
        try {
            java.lang.reflect.Field f = shop.getClass().getDeclaredField("activeItems");
            f.setAccessible(true);
            f.set(shop, newActive);
        } catch (Exception ignored) {}
        // Reset next rotation time
        long now = System.currentTimeMillis();
        long intervalMs = shopManager.parseInterval(shop.rotationInterval());
        java.lang.reflect.Field nextRotationTimesField;
        try {
            nextRotationTimesField = shopManager.getClass().getDeclaredField("nextRotationTimes");
            nextRotationTimesField.setAccessible(true);
            java.util.Map<String, Long> nextRotationTimes = (java.util.Map<String, Long>) nextRotationTimesField.get(shopManager);
            nextRotationTimes.put(shop.id(), now + intervalMs);
        } catch (Exception ignored) {}
        // Announce rotation if enabled
        java.lang.reflect.Field shopAnnouncementsField;
        try {
            shopAnnouncementsField = shopManager.getClass().getDeclaredField("shopAnnouncements");
            shopAnnouncementsField.setAccessible(true);
            java.util.Map<String, ?> shopAnnouncements = (java.util.Map<String, ?>) shopAnnouncementsField.get(shopManager);
            Object announcement = shopAnnouncements.get(shop.id());
            if (announcement != null) {
                java.lang.reflect.Field announceField = announcement.getClass().getDeclaredField("announce");
                java.lang.reflect.Field messageField = announcement.getClass().getDeclaredField("message");
                announceField.setAccessible(true);
                messageField.setAccessible(true);
                boolean announce = announceField.getBoolean(announcement);
                String msg = (String) messageField.get(announcement);
                if (announce) {
                    org.bukkit.Bukkit.broadcastMessage(msg.replace("%shop%", shop.title()));
                }
            }
        } catch (Exception ignored) {}
        messageService.send(sender, "admin.rotate_success", Placeholder.unparsed("shop", shopId));
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bshop.admin.view")) {
            messageService.send(sender, "no_permission");
            return;
        }

        if (args.length < 2) {
            messageService.send(sender, "admin.view_usage");
            return;
        }

        if (!(sender instanceof Player)) {
            messageService.send(sender, "player_only_command");
            return;
        }

        String targetUsername = args[1];
        org.bukkit.OfflinePlayer targetPlayer = org.bukkit.Bukkit.getOfflinePlayer(targetUsername);

        if (!targetPlayer.hasPlayedBefore()) {
            messageService.send(sender, "admin.view_player_not_found", Placeholder.unparsed("player", targetUsername));
            return;
        }

        // Open the recent purchases GUI for the target player
        shopGuiManager.openRecentPurchasesMenuForPlayer((Player) sender, targetPlayer.getUniqueId());
    }
}
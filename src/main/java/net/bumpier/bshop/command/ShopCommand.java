package net.bumpier.bshop.command;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.module.ModuleManager;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.util.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

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

        if (subCommand.equals("multiplier")) {
            handleMultiplier(sender, args);
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
            sender.sendMessage(ChatColor.RED + "Usage: /shop rotate <shop_id>");
            return;
        }

        String shopId = args[1];
        try {
            // Use reflection to access private fields for rotation
            var shop = shopManager.getShop(shopId);
            if (shop == null) {
                sender.sendMessage(ChatColor.RED + "Shop not found: " + shopId);
                return;
            }

            if (shop.type() == null || !shop.type().equalsIgnoreCase("rotational")) {
                sender.sendMessage(ChatColor.RED + "Shop is not rotational: " + shopId);
                return;
            }

            // Force rotation by clearing active items cache
            var shopClass = shop.getClass();
            var activeItemsField = shopClass.getDeclaredField("activeItems");
            activeItemsField.setAccessible(true);
            activeItemsField.set(shop, null);

            // Clear next rotation time to force immediate rotation
            var nextRotationTimesField = shopClass.getDeclaredField("nextRotationTimes");
            nextRotationTimesField.setAccessible(true);
            var nextRotationTimes = (java.util.Map<String, Long>) nextRotationTimesField.get(shop);
            nextRotationTimes.remove(shopId);

            sender.sendMessage(ChatColor.GREEN + "Forced rotation for shop: " + shopId);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to rotate shop: " + e.getMessage());
        }
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
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUsername);
        if (!targetPlayer.hasPlayedBefore()) {
            messageService.send(sender, "admin.view_player_not_found", Placeholder.unparsed("player", targetUsername));
            return;
        }
        shopGuiManager.openRecentPurchasesMenuForPlayer((Player) sender, targetPlayer.getUniqueId());
    }

    private void handleMultiplier(CommandSender sender, String[] args) {
        if (args.length < 2) {
            showMultiplierHelp(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "give":
                if (!sender.hasPermission("bshop.admin.multiplier.give")) {
                    messageService.send(sender, "multiplier.no_permission_give");
                    return;
                }
                if (args.length < 4) {
                    messageService.send(sender, "multiplier.usage_give");
                    return;
                }
                giveMultiplier(sender, args[2], args[3]);
                break;
            case "remove":
                if (!sender.hasPermission("bshop.admin.multiplier.remove")) {
                    messageService.send(sender, "multiplier.no_permission_remove");
                    return;
                }
                if (args.length < 3) {
                    messageService.send(sender, "multiplier.usage_remove");
                    return;
                }
                removeMultiplier(sender, args[2]);
                break;
            case "check":
                if (args.length < 3) {
                    messageService.send(sender, "multiplier.usage_check");
                    return;
                }
                checkMultiplier(sender, args[2]);
                break;
            case "list":
                if (!sender.hasPermission("bshop.admin.multiplier.list")) {
                    messageService.send(sender, "multiplier.no_permission_list");
                    return;
                }
                listMultipliers(sender);
                break;
            case "clear":
                if (!sender.hasPermission("bshop.admin.multiplier.clear")) {
                    messageService.send(sender, "multiplier.no_permission_clear");
                    return;
                }
                clearMultipliers(sender);
                break;
            default:
                showMultiplierHelp(sender);
                break;
        }
    }

    private void giveMultiplier(CommandSender sender, String playerName, String multiplierStr) {
        try {
            double multiplier = Double.parseDouble(multiplierStr);
            if (multiplier <= 0) {
                messageService.send(sender, "multiplier.give_invalid_value");
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
            if (!targetPlayer.hasPlayedBefore()) {
                messageService.send(sender, "multiplier.give_player_not_found", 
                    Placeholder.unparsed("player", playerName));
                return;
            }

            boolean success = BShop.getInstance().getMultiplierService().setTemporaryMultiplier(targetPlayer.getUniqueId(), multiplier);
            if (success) {
                messageService.send(sender, "multiplier.give_success", 
                    Placeholder.unparsed("player", targetPlayer.getName()),
                    Placeholder.unparsed("multiplier", String.valueOf(multiplier)));
                
                // Notify online player
                if (targetPlayer.isOnline()) {
                    Player onlinePlayer = targetPlayer.getPlayer();
                    if (onlinePlayer != null) {
                        messageService.send(onlinePlayer, "multiplier.give_received",
                            Placeholder.unparsed("multiplier", String.valueOf(multiplier)));
                    }
                }
            } else {
                messageService.send(sender, "multiplier.give_failed",
                    Placeholder.unparsed("player", targetPlayer.getName()));
            }
        } catch (NumberFormatException e) {
            messageService.send(sender, "multiplier.give_invalid_number",
                Placeholder.unparsed("value", multiplierStr));
        }
    }

    private void removeMultiplier(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore()) {
            messageService.send(sender, "multiplier.give_player_not_found",
                Placeholder.unparsed("player", playerName));
            return;
        }

        boolean removed = BShop.getInstance().getMultiplierService().removeTemporaryMultiplier(targetPlayer.getUniqueId());
        if (removed) {
            messageService.send(sender, "multiplier.remove_success",
                Placeholder.unparsed("player", targetPlayer.getName()));
            
            // Notify online player
            if (targetPlayer.isOnline()) {
                Player onlinePlayer = targetPlayer.getPlayer();
                if (onlinePlayer != null) {
                    messageService.send(onlinePlayer, "multiplier.remove_received");
                }
            }
        } else {
            messageService.send(sender, "multiplier.remove_no_multiplier",
                Placeholder.unparsed("player", targetPlayer.getName()));
        }
    }

    private void checkMultiplier(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore()) {
            messageService.send(sender, "multiplier.give_player_not_found",
                Placeholder.unparsed("player", playerName));
            return;
        }

        var multiplierService = BShop.getInstance().getMultiplierService();
        Double tempMultiplier = multiplierService.getTemporaryMultiplier(targetPlayer.getUniqueId());
        String currentMultiplier = multiplierService.getMultiplierDisplay(targetPlayer.getUniqueId());
        
        messageService.send(sender, "multiplier.check_header",
            Placeholder.unparsed("player", targetPlayer.getName()));
        messageService.send(sender, "multiplier.check_current",
            Placeholder.unparsed("multiplier", currentMultiplier));
        
        if (tempMultiplier != null) {
            messageService.send(sender, "multiplier.check_temp",
                Placeholder.unparsed("multiplier", String.valueOf(tempMultiplier)));
        } else {
            messageService.send(sender, "multiplier.check_temp_none");
        }

        // Show permission-based multipliers if any
        if (targetPlayer.isOnline()) {
            Player onlinePlayer = targetPlayer.getPlayer();
            if (onlinePlayer != null) {
                var permissionMultipliers = multiplierService.getPermissionMultipliers();
                if (!permissionMultipliers.isEmpty()) {
                    messageService.send(sender, "multiplier.check_permission_header");
                    for (var entry : permissionMultipliers.entrySet()) {
                        if (onlinePlayer.hasPermission(entry.getKey())) {
                            messageService.send(sender, "multiplier.check_permission_entry",
                                Placeholder.unparsed("permission", entry.getKey()),
                                Placeholder.unparsed("multiplier", String.valueOf(entry.getValue())));
                        }
                    }
                }
            }
        }
    }

    private void listMultipliers(CommandSender sender) {
        var tempMultipliers = BShop.getInstance().getMultiplierService().getTemporaryMultipliers();
        
        if (tempMultipliers.isEmpty()) {
            messageService.send(sender, "multiplier.list_empty");
            return;
        }

        messageService.send(sender, "multiplier.list_header");
        for (var entry : tempMultipliers.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String playerName = player.getName() != null ? player.getName() : entry.getKey().toString();
            messageService.send(sender, "multiplier.list_entry",
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("multiplier", String.valueOf(entry.getValue())));
        }
    }

    private void clearMultipliers(CommandSender sender) {
        BShop.getInstance().getMultiplierService().clearTemporaryMultipliers();
        messageService.send(sender, "multiplier.clear_success");
        
        // Notify all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (BShop.getInstance().getMultiplierService().hasTemporaryMultiplier(player)) {
                messageService.send(player, "multiplier.clear_received");
            }
        }
    }

    private void showMultiplierHelp(CommandSender sender) {
        messageService.send(sender, "multiplier.help_header");
        messageService.send(sender, "multiplier.help_give");
        messageService.send(sender, "multiplier.help_remove");
        messageService.send(sender, "multiplier.help_check");
        messageService.send(sender, "multiplier.help_list");
        messageService.send(sender, "multiplier.help_clear");
    }
}
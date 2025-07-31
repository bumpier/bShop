package net.bumpier.bshop.command;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.module.ModuleManager;
import net.bumpier.bshop.shop.ShopManager;
import net.bumpier.bshop.shop.ui.ShopGuiManager;
import net.bumpier.bshop.util.message.MessageService;
import net.bumpier.bshop.util.MultiplierService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShopCommand implements CommandExecutor, TabCompleter {

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

        if (subCommand.equals("debug")) {
            handleDebug(sender, args);
            return true;
        }

        if (!(sender instanceof Player)) {
            messageService.send(sender, "player_only_command");
            return true;
        }
        handleOpenCategory((Player) sender, subCommand);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - main subcommands
            List<String> subCommands = new ArrayList<>();
            
            // Add shop categories
            subCommands.addAll(shopManager.getLoadedShops().keySet());
            
            // Add admin commands if they have permission
            if (sender.hasPermission("bshop.admin.reload")) {
                subCommands.add("reload");
            }
            if (sender.hasPermission("bshop.admin.multiplier.give") || 
                sender.hasPermission("bshop.admin.multiplier.remove") || 
                sender.hasPermission("bshop.admin.multiplier.list") || 
                sender.hasPermission("bshop.admin.multiplier.clear")) {
                subCommands.add("multiplier");
            }
            if (sender.hasPermission("bshop.admin.rotate")) {
                subCommands.add("rotate");
            }
            if (sender.hasPermission("bshop.admin.view")) {
                subCommands.add("view");
            }
            if (sender.hasPermission("bshop.admin.debug")) {
                subCommands.add("debug");
            }

            // Filter based on what they've typed
            String input = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
        } else if (args.length == 2) {
            // Second argument - depends on first argument
            String firstArg = args[0].toLowerCase();
            
            if (firstArg.equals("multiplier")) {
                // Multiplier subcommands
                List<String> multiplierSubs = new ArrayList<>();
                if (sender.hasPermission("bshop.admin.multiplier.give")) {
                    multiplierSubs.add("give");
                }
                if (sender.hasPermission("bshop.admin.multiplier.remove")) {
                    multiplierSubs.add("remove");
                }
                if (sender.hasPermission("bshop.admin.multiplier.list")) {
                    multiplierSubs.add("list");
                }
                if (sender.hasPermission("bshop.admin.multiplier.clear")) {
                    multiplierSubs.add("clear");
                }
                if (sender.hasPermission("bshop.admin.multiplier.check")) {
                    multiplierSubs.add("check");
                }
                multiplierSubs.add("giveall");
                multiplierSubs.add("expire");
                multiplierSubs.add("stats");
                multiplierSubs.add("history");
                multiplierSubs.add("tiers");
                
                String input = args[1].toLowerCase();
                completions.addAll(multiplierSubs.stream()
                        .filter(sub -> sub.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("rotate")) {
                // Rotate subcommands
                List<String> rotateSubs = Arrays.asList("force", "schedule");
                String input = args[1].toLowerCase();
                completions.addAll(rotateSubs.stream()
                        .filter(sub -> sub.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("view")) {
                // View subcommands - player names
                String input = args[1].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("debug")) {
                // Debug subcommands - player names
                String input = args[1].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 3) {
            // Third argument - depends on previous arguments
            String firstArg = args[0].toLowerCase();
            String secondArg = args[1].toLowerCase();
            
            if (firstArg.equals("multiplier") && secondArg.equals("give")) {
                // Player names for multiplier give
                String input = args[2].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("multiplier") && secondArg.equals("remove")) {
                // Player names for multiplier remove
                String input = args[2].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("multiplier") && secondArg.equals("check")) {
                // Player names for multiplier check
                String input = args[2].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("multiplier") && secondArg.equals("giveall")) {
                // Player names for multiplier giveall
                String input = args[2].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("multiplier") && secondArg.equals("expire")) {
                // Player names for multiplier expire
                String input = args[2].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("multiplier") && secondArg.equals("history")) {
                // Player names for multiplier history
                String input = args[2].toLowerCase();
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (firstArg.equals("rotate") && secondArg.equals("force")) {
                // Shop names for rotate force
                String input = args[2].toLowerCase();
                completions.addAll(shopManager.getLoadedShops().keySet().stream()
                        .filter(shop -> shop.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 4) {
            // Fourth argument - multiplier value
            String firstArg = args[0].toLowerCase();
            String secondArg = args[1].toLowerCase();
            
            if (firstArg.equals("multiplier") && secondArg.equals("give")) {
                // Common multiplier values
                List<String> commonMultipliers = Arrays.asList("1.5", "2.0", "2.5", "3.0", "5.0", "10.0");
                String input = args[3].toLowerCase();
                completions.addAll(commonMultipliers.stream()
                        .filter(mult -> mult.startsWith(input))
                        .collect(Collectors.toList()));
            }
        }

        return completions;
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
        
        // Reload transaction service cooldowns
        BShop.getInstance().getTransactionService().reloadConfig();
        
        // Reload shops
        shopManager.loadShops();
        
        // Reload multipliers
        BShop.getInstance().getMultiplierService().reloadMultipliers();
        
        // Reload modules
        moduleManager.reloadModules();
        
        messageService.send(sender, "admin.reload_complete");
        // Removed automatic menu opening - only show success message
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
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", targetUsername);
            messageService.send(sender, "admin.view_player_not_found", placeholders);
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
                handleGiveMultiplier(sender, args);
                break;
            case "giveall":
                if (!sender.hasPermission("bshop.admin.multiplier.give")) {
                    messageService.send(sender, "multiplier.no_permission_give");
                    return;
                }
                if (args.length < 3) {
                    messageService.send(sender, "multiplier.usage_giveall");
                    return;
                }
                handleGiveAllMultiplier(sender, args);
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
            case "expire":
                if (!sender.hasPermission("bshop.admin.multiplier.remove")) {
                    messageService.send(sender, "multiplier.no_permission_remove");
                    return;
                }
                if (args.length < 3) {
                    messageService.send(sender, "multiplier.usage_expire");
                    return;
                }
                expireMultiplier(sender, args[2]);
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
            case "stats":
                if (!sender.hasPermission("bshop.admin.multiplier.stats")) {
                    messageService.send(sender, "multiplier.no_permission_stats");
                    return;
                }
                showMultiplierStats(sender);
                break;
            case "history":
                if (!sender.hasPermission("bshop.admin.multiplier.history")) {
                    messageService.send(sender, "multiplier.no_permission_history");
                    return;
                }
                if (args.length < 3) {
                    messageService.send(sender, "multiplier.usage_history");
                    return;
                }
                showMultiplierHistory(sender, args[2]);
                break;
            case "tiers":
                showAvailableTiers(sender);
                break;
            default:
                showMultiplierHelp(sender);
                break;
        }
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bshop.admin.debug")) {
            messageService.send(sender, "no_permission");
            return;
        }

        if (args.length < 2) {
            messageService.send(sender, "debug.usage");
            return;
        }

        String targetUsername = args[1];
        
        // Special case: debug config issues
        if (targetUsername.equalsIgnoreCase("config")) {
            BShop.getInstance().getMultiplierService().debugConfigIssues();
            sender.sendMessage("§aConfig debug information has been logged to console.");
            return;
        }
        
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUsername);
        if (!targetPlayer.hasPlayedBefore()) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", targetUsername);
            messageService.send(sender, "debug.player_not_found", placeholders);
            return;
        }

        var multiplierService = BShop.getInstance().getMultiplierService();
        
        // Show system status first
        sender.sendMessage("§6=== Multiplier System Status ===");
        sender.sendMessage("§aEnabled: §f" + multiplierService.isEnabled());
        sender.sendMessage("§aMax Multiplier: §f" + multiplierService.getMaxMultiplier());
        sender.sendMessage("§aPermission Multipliers Loaded: §f" + multiplierService.getPermissionMultipliers().size());
        
        // Show player-specific info
        java.util.Map<String, String> headerPlaceholders = new java.util.HashMap<>();
        headerPlaceholders.put("player", targetPlayer.getName());
        messageService.send(sender, "debug.header", headerPlaceholders);
        
        // Get the actual calculated multiplier for online players
        if (targetPlayer.isOnline()) {
            Player onlinePlayer = targetPlayer.getPlayer();
            if (onlinePlayer != null) {
                double actualMultiplier = multiplierService.getPlayerMultiplier(onlinePlayer);
                String currentMultiplier = String.format("%.1fx", actualMultiplier);
                
                java.util.Map<String, String> currentPlaceholders = new java.util.HashMap<>();
                currentPlaceholders.put("multiplier", currentMultiplier);
                messageService.send(sender, "debug.current_multiplier", currentPlaceholders);
                
                // Show permission multipliers
                var permissionMultipliers = multiplierService.getPermissionMultipliers();
                if (!permissionMultipliers.isEmpty()) {
                    messageService.send(sender, "debug.permission_multipliers_header");
                    for (var entry : permissionMultipliers.entrySet()) {
                        boolean hasPermission = onlinePlayer.hasPermission(entry.getKey());
                        java.util.Map<String, String> permPlaceholders = new java.util.HashMap<>();
                        permPlaceholders.put("permission", entry.getKey());
                        permPlaceholders.put("multiplier", String.valueOf(entry.getValue()));
                        permPlaceholders.put("has_permission", hasPermission ? "§aYes" : "§cNo");
                        sender.sendMessage("§7  • " + entry.getKey() + ": " + entry.getValue() + "x (" + permPlaceholders.get("has_permission") + "§7)");
                    }
                }
            }
        } else {
            // For offline players, only show temporary multiplier
            String currentMultiplier = multiplierService.getMultiplierDisplay(targetPlayer.getUniqueId());
            java.util.Map<String, String> currentPlaceholders = new java.util.HashMap<>();
            currentPlaceholders.put("multiplier", currentMultiplier);
            messageService.send(sender, "debug.current_multiplier", currentPlaceholders);
        }
        
        // Show temporary multiplier info
        var tempData = multiplierService.getTemporaryMultiplierData(targetPlayer.getUniqueId());
        if (tempData != null) {
            java.util.Map<String, String> tempPlaceholders = new java.util.HashMap<>();
            tempPlaceholders.put("multiplier", String.valueOf(tempData.getMultiplier()));
            tempPlaceholders.put("time_remaining", tempData.getTimeRemainingFormatted());
            tempPlaceholders.put("reason", tempData.getReason());
            tempPlaceholders.put("granted_by", tempData.getGrantedBy());
            sender.sendMessage("§aTemporary Multiplier: §f" + tempData.getMultiplier() + "x (expires: " + tempData.getTimeRemainingFormatted() + ")");
            sender.sendMessage("§aReason: §f" + tempData.getReason());
            sender.sendMessage("§aGranted by: §f" + tempData.getGrantedBy());
        } else {
            messageService.send(sender, "debug.temp_multiplier_none");
        }
        
        // Show other debug info
        if (targetPlayer.isOnline()) {
            Player onlinePlayer = targetPlayer.getPlayer();
            if (onlinePlayer != null) {
                double balance = BShop.getInstance().getEconomy().getBalance(onlinePlayer);
                java.util.Map<String, String> balancePlaceholders = new java.util.HashMap<>();
                balancePlaceholders.put("balance", String.format("%,.2f", balance));
                messageService.send(sender, "debug.balance", balancePlaceholders);
            }
        }
        
        sender.sendMessage("§6==========================================");
    }

    private void handleGiveMultiplier(CommandSender sender, String[] args) {
        String playerName = args[2];
        String multiplierStr = args[3];
        
        // Parse duration and reason
        long durationMs = 0;
        String reason = "No reason provided";
        String grantedBy = sender.getName();
        
        if (args.length > 4) {
            durationMs = parseDuration(args[4]);
            if (durationMs < 0) {
                messageService.send(sender, "multiplier.give_invalid_duration");
                return;
            }
        }
        
        if (args.length > 5) {
            reason = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        }
        
        try {
            double multiplier = Double.parseDouble(multiplierStr);
            if (multiplier <= 0) {
                messageService.send(sender, "multiplier.give_invalid_value");
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
            if (!targetPlayer.hasPlayedBefore()) {
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("player", playerName);
                messageService.send(sender, "multiplier.give_player_not_found", placeholders);
                return;
            }

            boolean success = BShop.getInstance().getMultiplierService().setTemporaryMultiplier(
                targetPlayer.getUniqueId(), multiplier, durationMs, reason, grantedBy);
                
            if (success) {
                java.util.Map<String, String> successPlaceholders = new java.util.HashMap<>();
                successPlaceholders.put("player", targetPlayer.getName());
                successPlaceholders.put("multiplier", String.valueOf(multiplier));
                if (durationMs > 0) {
                    successPlaceholders.put("duration", formatDuration(durationMs));
                } else {
                    successPlaceholders.put("duration", "Permanent");
                }
                messageService.send(sender, "multiplier.give_success", successPlaceholders);
            } else {
                java.util.Map<String, String> failedPlaceholders = new java.util.HashMap<>();
                failedPlaceholders.put("player", targetPlayer.getName());
                messageService.send(sender, "multiplier.give_failed", failedPlaceholders);
            }
        } catch (NumberFormatException e) {
            java.util.Map<String, String> invalidPlaceholders = new java.util.HashMap<>();
            invalidPlaceholders.put("value", multiplierStr);
            messageService.send(sender, "multiplier.give_invalid_number", invalidPlaceholders);
        }
    }

    private void handleGiveAllMultiplier(CommandSender sender, String[] args) {
        String multiplierStr = args[2];
        
        // Parse duration and reason
        long durationMs = 0;
        String reason = "No reason provided";
        String grantedBy = sender.getName();
        
        if (args.length > 3) {
            durationMs = parseDuration(args[3]);
            if (durationMs < 0) {
                messageService.send(sender, "multiplier.give_invalid_duration");
                return;
            }
        }
        
        if (args.length > 4) {
            reason = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        }
        
        try {
            double multiplier = Double.parseDouble(multiplierStr);
            if (multiplier <= 0) {
                messageService.send(sender, "multiplier.give_invalid_value");
                return;
            }

            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean success = BShop.getInstance().getMultiplierService().setTemporaryMultiplier(
                    player.getUniqueId(), multiplier, durationMs, reason, grantedBy);
                if (success) count++;
            }
            
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("count", String.valueOf(count));
            placeholders.put("multiplier", String.valueOf(multiplier));
            messageService.send(sender, "multiplier.giveall_success", placeholders);
            
            // Broadcast to all players
            java.util.Map<String, String> broadcastPlaceholders = new java.util.HashMap<>();
            broadcastPlaceholders.put("multiplier", String.valueOf(multiplier));
            BShop.getInstance().getMultiplierService().broadcastMultiplierEvent("giveall", broadcastPlaceholders);
            
        } catch (NumberFormatException e) {
            java.util.Map<String, String> invalidPlaceholders = new java.util.HashMap<>();
            invalidPlaceholders.put("value", multiplierStr);
            messageService.send(sender, "multiplier.give_invalid_number", invalidPlaceholders);
        }
    }

    private void removeMultiplier(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore()) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", playerName);
            messageService.send(sender, "multiplier.give_player_not_found", placeholders);
            return;
        }

        boolean removed = BShop.getInstance().getMultiplierService().removeTemporaryMultiplier(targetPlayer.getUniqueId());
        if (removed) {
            java.util.Map<String, String> successPlaceholders = new java.util.HashMap<>();
            successPlaceholders.put("player", targetPlayer.getName());
            messageService.send(sender, "multiplier.remove_success", successPlaceholders);
            
            // Notify online player
            if (targetPlayer.isOnline()) {
                Player onlinePlayer = targetPlayer.getPlayer();
                if (onlinePlayer != null) {
                    messageService.send(onlinePlayer, "multiplier.remove_received");
                }
            }
        } else {
            java.util.Map<String, String> noMultiplierPlaceholders = new java.util.HashMap<>();
            noMultiplierPlaceholders.put("player", targetPlayer.getName());
            messageService.send(sender, "multiplier.remove_no_multiplier", noMultiplierPlaceholders);
        }
    }

    private void expireMultiplier(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore()) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", playerName);
            messageService.send(sender, "multiplier.give_player_not_found", placeholders);
            return;
        }

        boolean expired = BShop.getInstance().getMultiplierService().expireTemporaryMultiplier(targetPlayer.getUniqueId());
        if (expired) {
            java.util.Map<String, String> successPlaceholders = new java.util.HashMap<>();
            successPlaceholders.put("player", targetPlayer.getName());
            messageService.send(sender, "multiplier.expire_success", successPlaceholders);
        } else {
            java.util.Map<String, String> noMultiplierPlaceholders = new java.util.HashMap<>();
            noMultiplierPlaceholders.put("player", targetPlayer.getName());
            messageService.send(sender, "multiplier.remove_no_multiplier", noMultiplierPlaceholders);
        }
    }

    private void checkMultiplier(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore()) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", playerName);
            messageService.send(sender, "multiplier.give_player_not_found", placeholders);
            return;
        }

        var multiplierService = BShop.getInstance().getMultiplierService();
        var tempData = multiplierService.getTemporaryMultiplierData(targetPlayer.getUniqueId());
        
        java.util.Map<String, String> headerPlaceholders = new java.util.HashMap<>();
        headerPlaceholders.put("player", targetPlayer.getName());
        messageService.send(sender, "multiplier.check_header", headerPlaceholders);
        
        // Get the actual calculated multiplier for online players
        if (targetPlayer.isOnline()) {
            Player onlinePlayer = targetPlayer.getPlayer();
            if (onlinePlayer != null) {
                // Get detailed multiplier information
                var multiplierInfo = multiplierService.getMultiplierInfo(onlinePlayer);
                String currentMultiplier = multiplierInfo.getFormattedTotal();
                
                java.util.Map<String, String> currentPlaceholders = new java.util.HashMap<>();
                currentPlaceholders.put("multiplier", currentMultiplier);
                messageService.send(sender, "multiplier.check_current", currentPlaceholders);
                
                // Show tier information
                if (multiplierInfo.hasTierMultipliers()) {
                    messageService.send(sender, "multiplier.check_tier_header");
                    for (var tier : multiplierInfo.getActiveTiers()) {
                        java.util.Map<String, String> tierPlaceholders = new java.util.HashMap<>();
                        tierPlaceholders.put("tier_name", tier.getName());
                        tierPlaceholders.put("tier_multiplier", tier.getFormattedMultiplier(false));
                        tierPlaceholders.put("permission", tier.getPermission());
                        messageService.send(sender, "multiplier.check_tier_entry", tierPlaceholders);
                    }
                } else {
                    messageService.send(sender, "multiplier.check_tier_none");
                }
                
                // Show legacy permission multipliers if any
                var permissionMultipliers = multiplierService.getPermissionMultipliers();
                if (!permissionMultipliers.isEmpty()) {
                    messageService.send(sender, "multiplier.check_legacy_header");
                    for (var entry : permissionMultipliers.entrySet()) {
                        if (onlinePlayer.hasPermission(entry.getKey())) {
                            java.util.Map<String, String> permPlaceholders = new java.util.HashMap<>();
                            permPlaceholders.put("permission", entry.getKey());
                            permPlaceholders.put("multiplier", String.valueOf(entry.getValue()));
                            messageService.send(sender, "multiplier.check_legacy_entry", permPlaceholders);
                        }
                    }
                }
            }
        } else {
            // For offline players, only show temporary multiplier
            String currentMultiplier = multiplierService.getMultiplierDisplay(targetPlayer.getUniqueId());
            java.util.Map<String, String> currentPlaceholders = new java.util.HashMap<>();
            currentPlaceholders.put("multiplier", currentMultiplier);
            messageService.send(sender, "multiplier.check_current", currentPlaceholders);
        }
        
        if (tempData != null) {
            java.util.Map<String, String> tempPlaceholders = new java.util.HashMap<>();
            tempPlaceholders.put("multiplier", String.valueOf(tempData.getMultiplier()));
            tempPlaceholders.put("time_remaining", tempData.getTimeRemainingFormatted());
            tempPlaceholders.put("reason", tempData.getReason());
            tempPlaceholders.put("granted_by", tempData.getGrantedBy());
            messageService.send(sender, "multiplier.check_temp", tempPlaceholders);
        } else {
            messageService.send(sender, "multiplier.check_temp_none");
        }
    }

    private void listMultipliers(CommandSender sender) {
        var tempMultipliers = BShop.getInstance().getMultiplierService().getTemporaryMultipliersData();
        
        if (tempMultipliers.isEmpty()) {
            messageService.send(sender, "multiplier.list_empty");
            return;
        }

        messageService.send(sender, "multiplier.list_header");
        for (var entry : tempMultipliers.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String playerName = player.getName() != null ? player.getName() : entry.getKey().toString();
            
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", playerName);
            placeholders.put("multiplier", String.valueOf(entry.getValue().getMultiplier()));
            placeholders.put("time_remaining", entry.getValue().getTimeRemainingFormatted());
            placeholders.put("reason", entry.getValue().getReason());
            
            messageService.send(sender, "multiplier.list_entry", placeholders);
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
        messageService.send(sender, "multiplier.help_giveall");
        messageService.send(sender, "multiplier.help_remove");
        messageService.send(sender, "multiplier.help_expire");
        messageService.send(sender, "multiplier.help_check");
        messageService.send(sender, "multiplier.help_list");
        messageService.send(sender, "multiplier.help_clear");
        messageService.send(sender, "multiplier.help_stats");
        messageService.send(sender, "multiplier.help_history");
        messageService.send(sender, "multiplier.help_tiers");
    }

    private void showMultiplierStats(CommandSender sender) {
        var multiplierService = BShop.getInstance().getMultiplierService();
        var stats = multiplierService.getMultiplierStats();
        
        messageService.send(sender, "multiplier.stats_header");
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        
        placeholders.put("count", String.valueOf(stats.get("active_temporary_multipliers")));
        messageService.send(sender, "multiplier.stats_active", placeholders);
        
        placeholders.put("count", String.valueOf(stats.get("expired_multipliers")));
        messageService.send(sender, "multiplier.stats_expired", placeholders);
        
        placeholders.put("count", String.valueOf(stats.get("permission_multipliers")));
        messageService.send(sender, "multiplier.stats_permission", placeholders);
        
        placeholders.put("average", String.format("%.2f", stats.get("average_multiplier")));
        messageService.send(sender, "multiplier.stats_average", placeholders);
        
        placeholders.put("size", String.valueOf(stats.get("cache_size")));
        messageService.send(sender, "multiplier.stats_cache", placeholders);
    }

    private void showMultiplierHistory(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore()) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", playerName);
            messageService.send(sender, "multiplier.give_player_not_found", placeholders);
            return;
        }

        var multiplierService = BShop.getInstance().getMultiplierService();
        var history = multiplierService.getMultiplierHistory(targetPlayer.getUniqueId());
        
        java.util.Map<String, String> headerPlaceholders = new java.util.HashMap<>();
        headerPlaceholders.put("player", targetPlayer.getName());
        messageService.send(sender, "multiplier.check_header", headerPlaceholders);
        
        if (history.isEmpty()) {
            messageService.send(sender, "multiplier.check_temp_none");
            return;
        }
        
        for (var entry : history) {
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("multiplier", String.valueOf(entry.multiplier));
            placeholders.put("reason", entry.reason);
            placeholders.put("granted_by", entry.grantedBy);
            
            java.util.Date date = new java.util.Date(entry.timestamp);
            placeholders.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));
            
            messageService.send(sender, "multiplier.history_entry", placeholders);
        }
    }

    // Utility methods
    private long parseDuration(String duration) {
        if (duration.equalsIgnoreCase("permanent") || duration.equalsIgnoreCase("perm")) {
            return 0;
        }
        
        try {
            char unit = duration.charAt(duration.length() - 1);
            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
            
            return switch (unit) {
                case 's' -> value * 1000;
                case 'm' -> value * 60 * 1000;
                case 'h' -> value * 60 * 60 * 1000;
                case 'd' -> value * 24 * 60 * 60 * 1000;
                default -> -1;
            };
        } catch (Exception e) {
            return -1;
        }
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) return "Permanent";
        
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
    
    private void showAvailableTiers(CommandSender sender) {
        var multiplierService = BShop.getInstance().getMultiplierService();
        var tiersByCategory = multiplierService.getTiersByCategory();
        
        if (tiersByCategory.isEmpty()) {
            messageService.send(sender, "multiplier.tiers_empty");
            return;
        }
        
        messageService.send(sender, "multiplier.tiers_header");
        
        for (var categoryEntry : tiersByCategory.entrySet()) {
            String category = categoryEntry.getKey();
            List<MultiplierService.MultiplierTier> tiers = categoryEntry.getValue();
            
            // Show category header
            java.util.Map<String, String> categoryPlaceholders = new java.util.HashMap<>();
            categoryPlaceholders.put("category", category.substring(0, 1).toUpperCase() + category.substring(1));
            messageService.send(sender, "multiplier.tiers_category_header", categoryPlaceholders);
            
            // Show tiers in this category
            for (MultiplierService.MultiplierTier tier : tiers) {
                java.util.Map<String, String> tierPlaceholders = new java.util.HashMap<>();
                tierPlaceholders.put("tier_name", tier.getName());
                tierPlaceholders.put("tier_multiplier", tier.getFormattedMultiplier(false));
                tierPlaceholders.put("permission", tier.getPermission());
                messageService.send(sender, "multiplier.tiers_tier_entry", tierPlaceholders);
            }
        }
    }
}
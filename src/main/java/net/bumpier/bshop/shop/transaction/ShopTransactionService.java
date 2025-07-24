package net.bumpier.bshop.shop.transaction;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.shop.model.ShopItem;
import net.bumpier.bshop.util.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ShopTransactionService {

    private final BShop plugin;
    private final Economy economy;
    private final MessageService messageService;

    public ShopTransactionService(BShop plugin, MessageService messageService) {
        this.plugin = plugin;
        this.economy = plugin.getEconomy();
        this.messageService = messageService;
    }

    public void buyItem(Player player, ShopItem item, int quantity) {
        if (item.isCommandBased() && item.getBuyCommand() != null) {
            String command = item.getBuyCommand()
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(quantity))
                .replace("{quantity}", String.valueOf(quantity));
            if (command.startsWith("/")) command = command.substring(1);
            if (command.startsWith("console:")) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command.substring(8).trim());
            } else {
                player.performCommand(command);
            }
            messageService.send(player, "shop.buy_success",
                Placeholder.unparsed("amount", String.valueOf(quantity)),
                Placeholder.component("item_name", messageService.parse(item.displayName())),
                Placeholder.unparsed("price", String.format("%,.2f", item.buyPrice() * quantity))
            );
            return;
        }
        if (item.buyPrice() <= 0) {
            messageService.send(player, "shop.buy_disabled");
            return;
        }

        double totalPrice = item.buyPrice() * quantity;
        double balance = economy.getBalance(player);
        if (balance < totalPrice) {
            messageService.send(player, "shop.insufficient_funds",
                    Placeholder.unparsed("price", String.format("%,.2f", totalPrice)),
                    Placeholder.unparsed("balance", String.format("%,.2f", balance))
            );
            return;
        }

        if (player.getInventory().firstEmpty() == -1) { // Basic check, can be improved
            messageService.send(player, "shop.inventory_full");
            return;
        }

        economy.withdrawPlayer(player, totalPrice);
        player.getInventory().addItem(new ItemStack(item.material(), quantity));

        messageService.send(player, "shop.buy_success",
                Placeholder.unparsed("amount", String.valueOf(quantity)),
                Placeholder.component("item_name", messageService.parse(item.displayName())),
                Placeholder.unparsed("price", String.format("%,.2f", totalPrice))
        );
    }

    public void sellItem(Player player, ShopItem item, int quantity) {
        if (item.isCommandBased() && item.getSellCommand() != null) {
            String command = item.getSellCommand()
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(quantity))
                .replace("{quantity}", String.valueOf(quantity));
            if (command.startsWith("/")) command = command.substring(1);
            if (command.startsWith("console:")) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command.substring(8).trim());
            } else {
                player.performCommand(command);
            }
            messageService.send(player, "shop.sell_success",
                Placeholder.unparsed("amount", String.valueOf(quantity)),
                Placeholder.component("item_name", messageService.parse(item.displayName())),
                Placeholder.unparsed("price", String.format("%,.2f", item.sellPrice() * quantity))
            );
            return;
        }
        if (item.sellPrice() <= 0) {
            messageService.send(player, "shop.sell_disabled");
            return;
        }

        ItemStack toSell = new ItemStack(item.material(), quantity);
        if (!player.getInventory().containsAtLeast(toSell, quantity)) {
            messageService.send(player, "shop.item_not_owned",
                    Placeholder.unparsed("amount", String.valueOf(quantity)),
                    Placeholder.component("item_name", messageService.parse(item.displayName()))
            );
            return;
        }

        double totalPrice = item.sellPrice() * quantity;
        player.getInventory().removeItem(toSell);
        economy.depositPlayer(player, totalPrice);

        messageService.send(player, "shop.sell_success",
                Placeholder.unparsed("amount", String.valueOf(quantity)),
                Placeholder.component("item_name", messageService.parse(item.displayName())),
                Placeholder.unparsed("price", String.format("%,.2f", totalPrice))
        );
    }
}
package net.bumpier.bshop.util;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.util.message.MessageService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.inventory.meta.SkullMeta;
import java.lang.reflect.Field;
import java.util.UUID;

public class ItemBuilder {

    private final BShop plugin;
    private final ItemStack itemStack;
    private final MessageService messageService;

    public ItemBuilder(BShop plugin, Material material, MessageService messageService) {
        this.plugin = plugin;
        this.itemStack = new ItemStack(material);
        this.messageService = messageService;
    }

    public ItemBuilder withDisplayName(String displayName) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && displayName != null) {
            meta.setDisplayName(messageService.serialize(messageService.parse(displayName)));
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withLore(List<String> lore) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null && lore != null) {
            List<String> serializedLore = lore.stream()
                    .map(line -> messageService.serialize(messageService.parse(line)))
                    .collect(Collectors.toList());
            meta.setLore(serializedLore);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withCustomModelData(int modelData) {
        if (modelData > 0) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(modelData);
                itemStack.setItemMeta(meta);
            }
        }
        return this;
    }

    /**
     * Attaches a persistent string to the item's data container.
     * @param key The key for the data.
     * @param value The string value to store.
     * @return The ItemBuilder instance.
     */
    public ItemBuilder withPDCString(String key, String value) {
        if (value == null || value.isEmpty()) {
            return this;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withAmount(int amount) {
        if (amount > 0 && amount <= 64) {
            itemStack.setAmount(amount);
        }
        return this;
    }

    public ItemStack build() {
        return this.itemStack;
    }

    public static ItemStack createBase64Head(String base64, String displayName, List<String> lore, int customModelData) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), null);
            profile.getProperties().put("textures", new Property("textures", base64));
            try {
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            if (customModelData > 0) meta.setCustomModelData(customModelData);
            head.setItemMeta(meta);
        }
        return head;
    }
}
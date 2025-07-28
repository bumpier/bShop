package net.bumpier.bshop.util;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.util.message.MessageService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.inventory.meta.SkullMeta;
import java.lang.reflect.Field;
import java.util.UUID;
// Try both possible imports for XHead
// import com.cryptomorin.xseries.XHead;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.profiles.objects.Profileable;
import com.cryptomorin.xseries.profiles.builder.XSkull;
// import com.cryptomorin.xseries.head.XHead;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

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
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withLore(List<String> lore) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            // Always set lore, even if empty, to override vanilla tooltips
            List<String> serializedLore = lore != null
                ? lore.stream().map(line -> messageService.serialize(messageService.parse(line))).collect(Collectors.toList())
                : new java.util.ArrayList<>();
            meta.setLore(serializedLore);

            meta.addItemFlags(ItemFlag.values());

            // Special case: remove spawner tooltip
            if (meta instanceof BlockStateMeta) {
                BlockStateMeta bsm = (BlockStateMeta) meta;
                if (bsm.getBlockState() instanceof CreatureSpawner) {
                    CreatureSpawner spawner = (CreatureSpawner) bsm.getBlockState();
                    // Optionally set a default type or leave as is
                    // spawner.setSpawnedType(EntityType.PIG);
                    bsm.setBlockState(spawner);
                    // Explicitly clear lore to remove Minecraft's warning
                    bsm.setLore(serializedLore);
                    meta = bsm;
                }
            }

            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder withCustomModelData(int modelData) {
        if (modelData > 0) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(modelData);
                meta.addItemFlags(ItemFlag.values());
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

    public static ItemStack createBase64Head(String base64, String displayName, List<String> lore, int customModelData, MessageService messageService) {
        ItemStack head = XMaterial.PLAYER_HEAD.parseItem();
        Profileable profile = Profileable.detect(base64);
        ItemStack customHead = XSkull.of(head).profile(profile).apply();
        ItemMeta meta = customHead.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messageService.serialize(messageService.parse(displayName)));
            List<String> serializedLore = lore != null
                ? lore.stream().map(line -> messageService.serialize(messageService.parse(line))).collect(Collectors.toList())
                : new java.util.ArrayList<>();
            meta.setLore(serializedLore);
            if (customModelData > 0) meta.setCustomModelData(customModelData);
            meta.addItemFlags(ItemFlag.values());
            customHead.setItemMeta(meta);
        }
        return customHead;
    }

    public static ItemStack createSkull(String texture, String base64, String displayName, List<String> lore, int customModelData, MessageService messageService, @Nullable String playerName) {
        ItemStack head = XMaterial.PLAYER_HEAD.parseItem();
        String base64ToUse = null;
        if (texture != null && texture.startsWith("base64:")) {
            base64ToUse = texture.substring("base64:".length());
        } else if (base64 != null && !base64.isEmpty()) {
            base64ToUse = base64;
        }
        if (base64ToUse != null && !base64ToUse.isEmpty()) {
            Profileable profile = Profileable.detect(base64ToUse);
            ItemStack customHead = XSkull.of(head).profile(profile).apply();
            ItemMeta meta = customHead.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(messageService.serialize(messageService.parse(displayName)));
                List<String> serializedLore = lore != null
                    ? lore.stream().map(line -> messageService.serialize(messageService.parse(line))).collect(Collectors.toList())
                    : new java.util.ArrayList<>();
                meta.setLore(serializedLore);
                if (customModelData > 0) meta.setCustomModelData(customModelData);
                meta.addItemFlags(ItemFlag.values());
                customHead.setItemMeta(meta);
            }
            return customHead;
        }
        // fallback to default head
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messageService.serialize(messageService.parse(displayName)));
            List<String> serializedLore = lore != null
                ? lore.stream().map(line -> messageService.serialize(messageService.parse(line))).collect(Collectors.toList())
                : new java.util.ArrayList<>();
            meta.setLore(serializedLore);
            if (customModelData > 0) meta.setCustomModelData(customModelData);
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }
}
package net.bumpier.bshop.shop.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.json.JSONArray;
import org.json.JSONObject;
import net.bumpier.bshop.BShop;

public class ShopTransactionLogger {
    private static final File logFile = new File("plugins/bShop/logs/transactions.log");
    private static boolean enabled = true;
    private static boolean rotate = true;
    private static int maxFileSizeMB = 10;
    private static int maxBackupFiles = 5;
    static {
        try {
            FileConfiguration config = BShop.getInstance().getConfig();
            if (config.isConfigurationSection("logging")) {
                enabled = config.getBoolean("logging.enabled", true);
                rotate = config.getBoolean("logging.rotate", true);
                maxFileSizeMB = config.getInt("logging.max_file_size_mb", 10);
                maxBackupFiles = config.getInt("logging.max_backup_files", 5);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void logTransaction(
        String playerUuid, String playerName, String shopId, String itemName, String material,
        int amount, double price, String type, double balanceAfter, String transactionId, String date, String itemId
    ) {
        if (!enabled) return;
        try {
            // Rotate if needed
            if (rotate && logFile.exists() && logFile.length() > maxFileSizeMB * 1024L * 1024L) {
                rotateLogs();
            }
            logFile.getParentFile().mkdirs();
            String line = String.format(
                "%s | %s | %s | %s | %s | %s | %d | %.2f | %s | %.2f | %s | %s",
                date, playerUuid, playerName, shopId, itemName, material, amount, price, type, balanceAfter, transactionId, itemId
            );
            Files.write(
                logFile.toPath(),
                (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        maybeAlertAdmins(playerName, shopId, itemName, amount, price, type, itemId);
    }

    public static void maybeAlertAdmins(String playerName, String shopId, String itemName, int amount, double price, String type, String itemId) {
        FileConfiguration config = BShop.getInstance().getConfig();
        if (!config.getBoolean("alerts.enabled", true)) return;
        double threshold = config.getDouble("alerts.threshold", 100.0);
        if (price <= threshold) return;
        String msg = config.getString("alerts.message",
            "&c[Shop Alert] &e%player% %type% %amount%x %item% for $%price% in %shop%");
        msg = msg.replace("%player%", playerName)
                 .replace("%amount%", String.valueOf(amount))
                 .replace("%item%", itemName)
                 .replace("%price%", String.format("%,.2f", price))
                 .replace("%shop%", shopId)
                 .replace("%type%", type)
                 .replace("%item_id%", itemId)
                 .replace("%prefix%", net.bumpier.bshop.shop.ShopModule.globalMessageService == null ? "" : net.bumpier.bshop.shop.ShopModule.globalMessageService.prefix)
        ;
        // Use MiniMessage for formatting
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(config.getString("alerts.permission", "bshop.alert"))) {
                net.bumpier.bshop.BShop.getInstance().adventure().sender(p).sendMessage(mm.deserialize(msg));
            }
        }
        // Discord webhook
        String webhookUrl = config.getString("alerts.discord_webhook_url", "");
        String payload = config.getString("alerts.discord_payload", "");
        if (webhookUrl != null && !webhookUrl.isEmpty() && payload != null && !payload.isEmpty()) {
            // Replace %date% with ISO8601 timestamp
            String isoDate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString();
            payload = payload.replace("%player%", playerName)
                             .replace("%amount%", String.valueOf(amount))
                             .replace("%item%", itemName)
                             .replace("%price%", String.format("%,.2f", price))
                             .replace("%shop%", shopId)
                             .replace("%type%", type)
                             .replace("%item_id%", itemId)
                             .replace("%date%", isoDate);
            sendDiscordWebhook(payload, webhookUrl);
        }
    }

    private static void rotateLogs() {
        try {
            // Delete oldest backup if exceeding maxBackupFiles
            File oldest = new File(logFile.getParent(), "transactions.log." + maxBackupFiles);
            if (oldest.exists()) oldest.delete();
            // Shift backups
            for (int i = maxBackupFiles - 1; i >= 1; i--) {
                File src = new File(logFile.getParent(), "transactions.log." + i);
                if (src.exists()) {
                    File dest = new File(logFile.getParent(), "transactions.log." + (i + 1));
                    src.renameTo(dest);
                }
            }
            // Rename current log to .1
            File firstBackup = new File(logFile.getParent(), "transactions.log.1");
            logFile.renameTo(firstBackup);
            // Create new log file
            logFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendDiscordWebhook(String payload, String webhookUrl) {
        try {
            java.net.URL url = new java.net.URL(webhookUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                java.io.InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    String errorMsg = new String(errorStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            conn.getInputStream().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 
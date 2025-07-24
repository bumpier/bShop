package net.bumpier.bshop.util.message;

import net.bumpier.bshop.BShop;
import net.bumpier.bshop.util.config.ConfigManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class MessageService {

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final ConfigManager messagesConfig;
    private final BukkitAudiences adventure;
    private final String prefix;

    public MessageService(BShop plugin, ConfigManager messagesConfig) {
        this.adventure = plugin.adventure();
        this.messagesConfig = messagesConfig;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.builder()
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.prefix = this.messagesConfig.getConfig().getString("prefix", "");
    }

    public Component parse(String text, TagResolver... resolvers) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String translated = legacySerializer.serialize(legacySerializer.deserialize(text));
        return miniMessage.deserialize(translated, resolvers);
    }

    public List<Component> parse(List<String> lines, TagResolver... resolvers) {
        return lines.stream()
                .map(line -> parse(line, resolvers))
                .collect(Collectors.toList());
    }

    public String serialize(Component component) {
        return legacySerializer.serialize(component);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String message = messagesConfig.getConfig().getString(key);
        if (message == null || message.isEmpty()) {
            Component errorMsg = Component.text("Missing message in messages.yml: " + key)
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED);
            adventure.sender(sender).sendMessage(errorMsg);
            return;
        }

        // Correctly merge resolvers
        TagResolver prefixResolver = Placeholder.component("prefix", parse(this.prefix));
        TagResolver finalResolvers = TagResolver.resolver(prefixResolver, TagResolver.resolver(resolvers));

        // Use the Adventure platform to send the component
        adventure.sender(sender).sendMessage(parse(message, finalResolvers));
    }
}
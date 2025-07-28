# bShop

A modern, modular, and high-performance GUI shop plugin for Minecraft (Spigot/Paper).

## Features
- **Fully GUI-based shops**: Intuitive, customizable shop menus
- **Rotational/Seasonal shops**: Fortnite-style item rotations, featured items, announcements
- **Custom currency support**: Vault, command-based, PlaceholderAPI integration
- **Permission-based sell price multipliers**: Reward VIPs or special groups
- **Purchase/sell limits**: Per-player, per-rotation
- **Manual and automatic shop rotation**
- **Custom GUI elements**: Decorative, non-interactive items
- **Transaction logging and alerts**: File-based logs, Discord webhook support
- **Highly configurable**: YAML for shops, GUIs, messages, and more
- **Public API**: Integrate with other plugins or extend bShop

## Getting Started

### Installation
1. Download the latest `bShop.jar` from the [releases page](https://github.com/bumpier/bshop/releases).
2. Place it in your server's `plugins/` directory.
3. Start your server. Configuration files will be generated automatically.

### Configuration
- **`config.yml`**: Core settings, database, multipliers, alerts
- **`messages.yml`**: All plugin messages, MiniMessage formatting
- **`guis.yml`**: GUI layouts and actions
- **`shops/`**: Shop definitions (standard and rotational)

See the [docs](docs/) for detailed configuration guides and examples.

## API Usage

bShop provides a public API for developers to integrate or extend the plugin.

### Maven/Gradle (via JitPack)

Add the JitPack repository and dependency to your plugin's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>com.github.bumpier</groupId>
        <artifactId>bshop-api</artifactId>
        <version>1.0.0</version> <!-- Use the latest release/tag -->
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Example Usage
```java
import net.bumpier.bshop.api.BShopAPI;
import net.bumpier.bshop.api.ShopAPI;

// Get the API instance
BShopAPI api = BShopAPI.getInstance();
ShopAPI shopAPI = new ShopAPI(api);

// Open a shop for a player
shopAPI.openShop(player, "blocks");
```

### API Features
- Access shops, items, and rotations
- Perform buy/sell transactions
- Open GUIs for players
- Listen for shop events (transactions, rotations)
- Query and apply permission-based multipliers

See the [API JavaDocs](https://jitpack.io/com/github/bumpier/bshop-api/) for full details.

## Contributing
Pull requests, issues, and suggestions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License
bShop is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

**bShop** by [bumpier.dev](https://github.com/bumpier) 
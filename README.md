# bShop - Modern GUI Shop Plugin

A modern, modular, and high-performance GUI shop plugin for Minecraft servers.

## Features

- **Modern GUI Interface**: Beautiful, responsive shop interface with MiniMessage support
- **Multiple Shop Types**: Advanced shops, rotational shops, and command-based shops
- **Permission-based Multipliers**: Flexible sell price multiplier system
- **Transaction Logging**: Comprehensive logging and alerting system
- **High Performance**: Optimized for large servers with caching and async processing
- **Database Support**: SQLite (default) and MySQL support
- **Discord Integration**: Webhook support for transaction alerts

## Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin using the generated configuration files

## Configuration

### Main Configuration (`config.yml`)

The main configuration file contains settings for:

- **Database**: Choose between SQLite (default) or MySQL
- **Multipliers**: Configure permission-based sell price multipliers
- **Logging**: Set up transaction logging and rotation
- **Alerts**: Configure transaction alerts and Discord webhooks
- **Performance**: Optimize for your server's needs
- **GUI**: Customize GUI behavior and animations
- **Security**: Anti-click spam and transaction limits

### GUI Configuration (`guis.yml`)

This file controls all GUI layouts and interactions:

- **Main Menu**: Shop category selection
- **Quantity Menu**: Item quantity selection with bulk options
- **Recent Purchases**: Transaction history display
- **Wallet**: Balance display and management

### Messages (`messages.yml`)

Customize all plugin messages with MiniMessage formatting support.

### Shop Files (`shops/`)

Individual shop configurations:

- `advanced_shop.yml`: Multi-page shop with limits
- `quick_rotational.yml`: Rotates every 12 hours
- `rotational_example.yml`: Event-themed shop (6-hour rotation)
- `command_based.yml`: Items that execute commands

## Commands

### Player Commands

- `/shop` - Open the main shop menu
- `/shop help` - Show help information

### Admin Commands

- `/shop reload` - Reload plugin configuration
- `/shop multiplier <subcommand>` - Manage sell multipliers
- `/shop rotate <shop>` - Force rotate a shop
- `/shop view <player>` - View player's recent transactions
- `/shop debug <player>` - Debug player information

### Multiplier Commands

- `/shop multiplier give <player> <multiplier>` - Give temporary multiplier
- `/shop multiplier remove <player>` - Remove temporary multiplier
- `/shop multiplier check <player>` - Check player's multiplier
- `/shop multiplier list` - List all active multipliers
- `/shop multiplier clear` - Clear all temporary multipliers

## Permissions

### Basic Permissions

- `bshop.use` - Use the shop plugin (default: true)
- `bshop.alert` - Receive transaction alerts (default: op)

### Admin Permissions

- `bshop.admin.reload` - Reload configuration (default: op)
- `bshop.admin.rotate` - Rotate shops (default: op)
- `bshop.admin.view` - View player transactions (default: op)
- `bshop.admin.debug` - Debug functionality (default: op)

### Multiplier Permissions

- `bshop.admin.multiplier.*` - All multiplier admin commands (default: op)
- `bshop.multiplier.0.8` - 0.8x sell multiplier (default: false)
- `bshop.multiplier.0.9` - 0.9x sell multiplier (default: false)
- `bshop.multiplier.1.1` - 1.1x sell multiplier (default: false)
- `bshop.multiplier.1.2` - 1.2x sell multiplier (default: false)
- `bshop.multiplier.1.5` - 1.5x sell multiplier (default: false)

## Shop Types

### Advanced Shop

- Multi-page support with pagination
- Buy/sell limits per item
- Comprehensive item configuration
- Example: `advanced_shop.yml`

### Rotational Shop

- Items rotate on a schedule
- Configurable rotation intervals
- Rotation announcements
- Examples: `quick_rotational.yml`, `rotational_example.yml`

### Command-Based Shop

- Items execute commands on purchase
- Supports both legacy single command and new separate buy/sell commands
- Perfect for effects, teleportation, custom currencies, etc.
- Example: `command_based.yml`

#### Command Configuration Options

**Legacy Format (Single Command)**:
```yaml
currency-command: "customcurrency take %player% money %amount%"
currency-requirement: "%customcurrency_money%"
```

**New Format (Separate Buy/Sell Commands)**:
```yaml
buy-currency-command: "customcurrency take %player% money %amount%"
sell-currency-command: "customcurrency give %player% money %amount%"
buy-currency-requirement: "%customcurrency_money%"
sell-currency-requirement: "%customcurrency_money%"
```

**Priority**: The plugin will use the separate buy/sell commands if configured, otherwise fall back to the legacy single command.

## Placeholders

### Standard Placeholders

- `%item%` - Item name
- `%amount%` - Transaction amount
- `%price%` - Transaction price
- `%type%` - Buy/Sell
- `%date%` - Transaction date
- `%shop%` - Shop name
- `%transaction_id%` - Unique transaction ID
- `%balance_after%` - Player's balance after transaction
- `%multiplier_display%` - Current multiplier display

### PlaceholderAPI Support

All PlaceholderAPI placeholders are supported in item lore and display names, including:
- `%vault_eco_balance_formatted%` - Formatted balance
- `%player%` - Player name
- And many more...

## Multiplier System

The plugin features a flexible multiplier system:

1. **Permission Multipliers**: Set via permissions (additive)
2. **Temporary Multipliers**: Given via admin commands
3. **Display**: Multipliers can be shown in item lore
4. **Maximum**: Configurable maximum multiplier value

## Database

### SQLite (Default)

No additional configuration required. Data is stored in `plugins/bShop/data.db`.

### MySQL

Configure in `config.yml`:

```yaml
database:
  type: "MySQL"
  mysql:
    host: "localhost"
    port: 3306
    database: "bshop"
    username: "user"
    password: "password"
```

## Performance Optimization

The plugin includes several performance features:

- **Caching**: Shop data caching with configurable duration
- **Async Processing**: Background transaction processing with configurable thread pools
- **Connection Pooling**: MySQL connection optimization
- **Memory Management**: Automatic cleanup and optimization
- **Configurable Cooldowns**: All cooldown values can be adjusted in `config.yml`

### Performance Configuration

```yaml
performance:
  database:
    connection_pool_size: 20
    minimum_idle: 5
  
  caching:
    enabled: true
    shop_cache_duration: 300000  # 5 minutes
    max_cached_shops: 100
  
  async:
    transaction_threads: 4
  
  memory:
    cleanup_interval: 300000  # 5 minutes
  
  cooldowns:
    gui_update: 100  # milliseconds
    inventory_check: 500  # milliseconds
    transaction: 100  # milliseconds
    auto_click_prevention: 100  # milliseconds
```

## Troubleshooting

### "Main menu not configured" Error

This error occurs when the `guis.yml` file is missing or corrupted. Ensure:

1. The `guis.yml` file exists in the plugin folder
2. The `main-menu` section is properly configured
3. All required items have valid configurations

### Database Issues

- **SQLite**: Ensure the plugin folder is writable
- **MySQL**: Check connection settings and database permissions

### Permission Issues

- Verify Vault is installed and an economy plugin is active
- Check permission node assignments
- Ensure players have `bshop.use` permission

## Support

For support and issues:

1. Check the configuration files for errors
2. Review server logs for detailed error messages
3. Ensure all dependencies are installed (Vault, PlaceholderAPI)
4. Verify your server version is compatible (1.20+)

## License

This plugin is licensed under the MIT License. See LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests. 
# bShop Multiplier Tiers System

## Overview

The bShop plugin now features a user-friendly tier-based multiplier system that replaces the old complex permission-based multipliers. This new system makes it much easier to understand, configure, and manage sell price multipliers.

## Key Features

### 🎯 **User-Friendly Design**
- **Named Tiers**: Instead of confusing permission nodes like `bshop.multiplier.1.1`, you now have clear names like "Bronze", "Silver", "Gold"
- **Simple Configuration**: Each tier only needs a name, multiplier value, and permission node
- **Categorized**: Tiers are organized into logical categories (Basic, VIP, Events, Custom)

### 🔧 **Easy Configuration**
- **Simple YAML**: Configure tiers in a clear, readable format
- **Flexible**: Add custom tiers for your server's specific needs
- **Backward Compatible**: Old permission multipliers still work during migration

### 📊 **Better Management**
- **Tier Information**: See all available tiers with `/shop multiplier tiers`
- **Detailed Player Info**: Check what tiers a player has with `/shop multiplier check <player>`
- **Clear Permissions**: Each tier has a specific permission node

## Configuration

### Basic Setup

In your `config.yml`, under the `multipliers` section:

```yaml
multipliers:
  enabled: true
  max_multiplier: 10.0
  
  # User-friendly multiplier tiers
  tiers:
    # Basic tiers - simple and easy to understand
    basic:
      bronze:
        name: "Bronze"
        multiplier: 1.1
        permission: "bshop.tier.bronze"
      
      silver:
        name: "Silver"
        multiplier: 1.25
        permission: "bshop.tier.silver"
      
      gold:
        name: "Gold"
        multiplier: 1.5
        permission: "bshop.tier.gold"
```

### Tier Properties

Each tier has the following properties:

- **`name`**: Display name for the tier (e.g., "Gold", "VIP")
- **`multiplier`**: The multiplier value (must be > 0)
- **`permission`**: Permission node required for this tier

### Categories

Tiers are organized into categories:

1. **`basic`**: Standard tiers (Bronze, Silver, Gold, Diamond)
2. **`vip`**: VIP-specific tiers
3. **`events`**: Event-based tiers (Double Sell, Triple Sell)
4. **`custom`**: Server-specific tiers (Staff, Donor)

## Commands

### View Available Tiers
```
/shop multiplier tiers
```
Shows all configured tiers organized by category.

### Check Player's Tiers
```
/shop multiplier check <player>
```
Shows detailed information about a player's active tiers and multipliers.

### Other Commands
- `/shop multiplier give <player> <multiplier> [duration] [reason]` - Give temporary multiplier
- `/shop multiplier remove <player>` - Remove temporary multiplier
- `/shop multiplier list` - List all active temporary multipliers
- `/shop multiplier stats` - Show multiplier statistics

## Migration from Old System

### Automatic Conversion
The new system automatically loads legacy permission multipliers from the `legacy_permission_multipliers` section:

```yaml
legacy_permission_multipliers:
  "bshop.multiplier.1.1": 1.1
  "bshop.multiplier.1.2": 1.2
  "bshop.multiplier.1.5": 1.5
```

### Migration Steps
1. **Backup** your current `config.yml`
2. **Add** the new tier configuration
3. **Move** old permission multipliers to `legacy_permission_multipliers`
4. **Test** the new system
5. **Remove** legacy section once migration is complete

## Permission Management

### Granting Tiers
Use your permission plugin (LuckPerms, PermissionsEx, etc.) to grant tier permissions:

```
/lp user <player> permission set bshop.tier.gold true
/lp user <player> permission set bshop.vip true
```

### Tier Permissions
- `bshop.tier.bronze` - Bronze tier (1.1x)
- `bshop.tier.silver` - Silver tier (1.25x)
- `bshop.tier.gold` - Gold tier (1.5x)
- `bshop.tier.diamond` - Diamond tier (2.0x)
- `bshop.vip` - VIP tier (1.75x)
- `bshop.premium` - Premium tier (2.5x)
- `bshop.event.double` - Double sell event (2.0x)
- `bshop.event.triple` - Triple sell event (3.0x)
- `bshop.staff` - Staff tier (1.3x)
- `bshop.donor` - Donor tier (1.4x)

## Examples

### Basic Server Setup
```yaml
tiers:
  basic:
    bronze:
      name: "Bronze"
      multiplier: 1.1
      permission: "bshop.tier.bronze"
    
    gold:
      name: "Gold"
      multiplier: 1.5
      permission: "bshop.tier.gold"
  
  custom:
    donor:
      name: "Donor"
      multiplier: 1.4
      permission: "bshop.donor"
```

### Event Setup
```yaml
tiers:
  events:
    double_sell:
      name: "Double Sell"
      multiplier: 2.0
      permission: "bshop.event.double"
    
    triple_sell:
      name: "Triple Sell"
      multiplier: 3.0
      permission: "bshop.event.triple"
```

## Benefits

### For Server Owners
- **Easy to understand**: Clear tier names and simple configuration
- **Simple to configure**: Intuitive YAML structure
- **Flexible**: Add custom tiers for your server's needs
- **Organized**: Logical categorization of tiers

### For Players
- **Clear benefits**: Easy to understand what each tier does
- **Transparent**: Can see their active tiers with commands

### For Administrators
- **Better management**: Clear overview of all available tiers
- **Easy debugging**: Detailed player information
- **Migration support**: Backward compatibility with old system

## Troubleshooting

### Common Issues

1. **Tiers not showing**: Check that `enabled: true` in the multipliers section
2. **Permissions not working**: Verify permission nodes are correctly set
3. **Multipliers not stacking**: Ensure `max_multiplier` is set high enough

### Debug Commands
- `/shop multiplier check <player>` - Check player's multiplier details
- `/shop debug <player>` - Debug multiplier calculations
- `/shop multiplier tiers` - List all configured tiers

## Support

If you encounter issues with the new tier system:
1. Check the console for error messages
2. Verify your YAML syntax
3. Test with a simple tier configuration first
4. Check that permissions are correctly assigned 
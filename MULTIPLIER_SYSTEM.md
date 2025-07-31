# bShop Multiplier System Documentation

## Overview

The bShop multiplier system allows players to receive bonus sell prices based on their permissions and temporary multipliers. The system uses **additive stacking**, meaning tier multipliers and temporary multipliers are added together to create the final multiplier.

## How It Works

### Multiplier Calculation
- **Base Multiplier**: 1.0x (no bonus)
- **Tier Multipliers**: Based on player permissions (e.g., VIP, Premium, etc.)
- **Temporary Multipliers**: Granted by admins for special events or bonuses
- **Final Multiplier**: Base + Tier + Temporary (capped at max_multiplier)

### Additive Stacking Explained
The system uses **additive stacking**, not multiplicative stacking. This means:

**Correct (Additive):**
- Bronze tier: +0.1 (adds 0.1 to base)
- 10x temporary: +9.0 (adds 9.0 to base)
- 2x event bonus: +1.0 (adds 1.0 to base)
- New player penalty: +0.8 (adds 0.8 to base)
- Total: 1.0 + 0.1 + 9.0 + 1.0 + 0.8 = 11.9x

**Incorrect (Multiplicative):**
- Bronze tier: ×1.1 (multiplies by 1.1)
- 10x temporary: ×10.0 (multiplies by 10.0)
- Total: 1.0 × 1.1 × 10.0 = 11.0x

**Important:** 
- The `max_multiplier` setting must be high enough to accommodate the sum of all multipliers
- Multiple temporary multipliers can be active simultaneously and will stack additively
- Each temporary multiplier is tracked separately with its own expiration time
- **Penalty multipliers** (0.0x to 1.0x) are supported and will add their value to the base multiplier

### Example
- Player has VIP tier (1.5x), temporary event multiplier (2.0x), weekend bonus (1.5x), and new player penalty (0.8x)
- Final multiplier = 1.0 + 1.5 + 2.0 + 1.5 + 0.8 = 6.8x
- If max_multiplier is 15.0, the final multiplier stays 6.8x
- If max_multiplier is 4.0, the final multiplier gets capped at 4.0x

## Configuration

### Basic Settings (config.yml)
```yaml
multipliers:
  enabled: true
  max_multiplier: 15.0  # Should accommodate tier + temporary multipliers
  cache_expiration_ms: 30000
  cleanup_interval_ms: 60000
  debug_logging: false  # Enable for troubleshooting
  
  display:
    show_in_lore: true
    format: "%multiplier%x"
    show_percentage_bonus: false
```

### Tier Configuration
```yaml
multipliers:
  tiers:
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
    vip:
      vip:
        name: "VIP"
        multiplier: 1.75
        permission: "bshop.vip"
      premium:
        name: "Premium"
        multiplier: 2.5
        permission: "bshop.premium"
    penalties:
      new_player:
        name: "New Player"
        multiplier: 0.8
        permission: "bshop.penalty.new"
      inactive:
        name: "Inactive"
        multiplier: 0.9
        permission: "bshop.penalty.inactive"
```

### How Penalty Multipliers Work
In the additive system, ALL multipliers (including penalties) are added to the base 1.0x:

- **Normal multiplier (1.5x)**: Adds 1.5 to base → 1.0 + 1.5 = 2.5x total
- **Penalty multiplier (0.8x)**: Adds 0.8 to base → 1.0 + 0.8 = 1.8x total
- **Zero multiplier (0.0x)**: Adds 0.0 to base → 1.0 + 0.0 = 1.0x total

This means penalty multipliers still increase the sell price, just by a smaller amount than normal multipliers.

## %multiplier_display% Placeholder

The `%multiplier_display%` placeholder shows the player's current multiplier in a configurable format. It can be used in:

- Shop item lore
- Quantity menu display
- Stack selection GUI
- Any other GUI text

### Display Formats

#### Default Format (1.5x)
```yaml
multipliers:
  display:
    format: "%multiplier%x"
    show_percentage_bonus: false
```
Result: `1.5x`

#### Percentage Format (+50%)
```yaml
multipliers:
  display:
    format: "%multiplier%x"
    show_percentage_bonus: true
```
Result: `+50%`

#### Custom Format
```yaml
multipliers:
  display:
    format: "⚡ %multiplier%x Bonus"
    show_percentage_bonus: false
```
Result: `⚡ 1.5x Bonus`

### Usage Examples

#### Shop Item Lore
```yaml
items:
  - id: diamond_sword
    material: DIAMOND_SWORD
    display-name: "<yellow>Diamond Sword"
    lore:
      - "<gray>Buy Price: <green>$100.00</green>"
      - "<gray>Sell Price: <red>$%sell_price%</red> <gold>(%multiplier_display%)</gold>"
      - ""
      - "<yellow>Click to buy or sell!"
    buy-price: 100.0
    sell-price: 50.0
```

#### Quantity Menu
```yaml
quantity-menu:
  display_item:
    sell_display-name: "<#e42121><bold>sᴇʟʟɪɴɢ</bold> <#ffffff>%item%"
    lore:
      - "<#BDBDBD>• Quantity: <#ffffff>%quantity%</#ffffff>"
      - "<#BDBDBD>• Price/Item: <#ffffff>%price_per_item%</#ffffff>"
      - "<#BDBDBD>• Multiplier: <#ffd700>%multiplier_display%</#ffd700>"
      - "<#BDBDBD>• Total Price: <#66ff2e>%total_price%</#66ff2e>"
```

#### Stack Selection GUI
```yaml
stack_gui:
  items:
    stack_1:
      material: CHEST
      sell_display-name: "<#e42121>sᴇʟʟ <#F19090>1</#F19090> sᴛᴀᴄᴋ"
      lore:
        - "<#BDBDBD>• Price: <#ffffff>%price%</#ffffff>"
        - "<#BDBDBD>• Multiplier: <#ffd700>%multiplier_display%</#ffd700>"
        - "<#BDBDBD><i>Click to select this amount.</i>"
```

## Available Placeholders

### Price Placeholders
- `%buy_price%` - Base buy price (unaffected by multipliers)
- `%sell_price%` - Sell price with multiplier applied

### Multiplier Placeholders
- `%multiplier_display%` - Formatted multiplier display
- `%multiplier%` - Raw multiplier value (for custom formatting)

### Other Placeholders
- `%item%` / `%item_name%` - Item display name
- `%quantity%` - Transaction quantity
- `%price_per_item%` - Price per individual item
- `%total_price%` - Total transaction price
- `%stacks%` - Number of stacks (in stack GUI)
- `%amount%` - Total amount in items
- `%timer%` - Rotation timer (for rotational shops)

## Commands

### Admin Commands
```bash
# Give temporary multiplier
/shop multiplier give <player> <multiplier> [duration] [reason]

# Give multiplier to all players
/shop multiplier giveall <multiplier> [duration] [reason]

# Remove multiplier
/shop multiplier remove <player>

# Check player's multiplier
/shop multiplier check <player>

# List all active multipliers
/shop multiplier list

# Clear all temporary multipliers
/shop multiplier clear

# Show multiplier statistics
/shop multiplier stats
```

### Examples
```bash
# Give 2x multiplier for 1 hour
/shop multiplier give PlayerName 2.0 1h "Double sell event"

# Give 1.5x multiplier permanently
/shop multiplier give PlayerName 1.5 permanent "VIP bonus"

# Give 3x multiplier to all players for 30 minutes
/shop multiplier giveall 3.0 30m "Triple sell weekend"
```

## Technical Details

### Multiplier Calculation Logic
1. Start with base multiplier (1.0)
2. Add all tier multipliers the player has permission for
3. Add all active temporary multipliers (multiple can be active simultaneously)
4. Cap at max_multiplier value
5. Apply to sell price: `final_price = base_price * multiplier`

### Multiple Temporary Multipliers
- Players can have multiple temporary multipliers active at the same time
- Each temporary multiplier is tracked separately with its own expiration time
- All active temporary multipliers are added together (additive stacking)
- When a temporary multiplier expires, only that specific multiplier is removed
- Other active multipliers continue to work normally

### Caching
- Multiplier calculations are cached for 30 seconds by default
- Cache is invalidated when temporary multipliers change
- Improves performance for frequent GUI updates

### Error Handling
- Invalid multipliers default to 1.0x
- Missing permissions are ignored
- Expired temporary multipliers are automatically cleaned up

## Best Practices

### Shop Configuration
1. Always include `%multiplier_display%` in sell price lore
2. Use consistent formatting across all items
3. Consider using percentage format for better user understanding

### Multiplier Management
1. Set reasonable max_multiplier limits (10-15x recommended to accommodate tier + temporary stacking)
2. Use descriptive reasons for temporary multipliers
3. Monitor multiplier statistics regularly
4. Clean up expired multipliers periodically
5. Enable debug_logging temporarily to troubleshoot multiplier calculations

### Performance
1. Keep cache expiration reasonable (30-60 seconds)
2. Avoid excessive temporary multipliers
3. Use tier-based multipliers for permanent bonuses
4. Reserve temporary multipliers for events

## Troubleshooting

### Common Issues

**Multiplier not showing in GUI**
- Check if `show_in_lore` is enabled in config
- Verify the `%multiplier_display%` placeholder is in the lore
- Ensure multipliers are enabled

**Incorrect multiplier values**
- Check player permissions
- Verify tier configuration
- Look for expired temporary multipliers

**Performance issues**
- Reduce cache expiration time
- Clear old temporary multipliers
- Check for excessive GUI updates

### Debug Commands
```bash
# Debug player multiplier
/shop debug <player>

# Show multiplier statistics
/shop multiplier stats

# List active multipliers
/shop multiplier list

# Enable debug logging in config.yml
multipliers:
  debug_logging: true
```

## Examples

### Complete Shop Item Example
```yaml
items:
  - id: diamond_sword
    material: DIAMOND_SWORD
    display-name: "<yellow>Diamond Sword"
    lore:
      - "<gray>Buy Price: <green>$%buy_price%</green>"
      - "<gray>Sell Price: <red>$%sell_price%</red>"
      - "<gray>Multiplier: <gold>%multiplier_display%</gold>"
      - ""
      - "<yellow>Click to buy or sell!"
      - "<gray><i>Higher multipliers = better sell prices!</i>"
    buy-price: 100.0
    sell-price: 50.0
    slot: 10
```

### Event Multiplier Example
```yaml
# In config.yml
multipliers:
  tiers:
    events:
      double_sell:
        name: "Double Sell Event"
        multiplier: 2.0
        permission: "bshop.event.double"
```

### Multiple Temporary Multipliers Example
```bash
# Start a weekend event
/shop multiplier giveall 2.5 48h "Weekend bonus event"

# Give VIP players extra bonus (stacks with weekend bonus)
/shop multiplier giveall 1.5 48h "VIP weekend bonus"

# Give a special event bonus (stacks with both above)
/shop multiplier giveall 3.0 2h "Special event bonus"

# Result for VIP player:
# Base: 1.0 + VIP tier: 1.75 + Weekend: 2.5 + VIP bonus: 1.5 + Special: 3.0 = 9.75x total
``` 
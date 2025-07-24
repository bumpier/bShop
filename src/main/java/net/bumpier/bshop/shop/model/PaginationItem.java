package net.bumpier.bshop.shop.model;

import org.bukkit.Material;
import java.util.List;

/**
 * Represents a configurable navigation or filler item in a paginated GUI.
 */
public record PaginationItem(
        Material material,
        String displayName,
        List<String> lore,
        int slot
) {}
package net.bumpier.bshop.shop.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a configurable shop GUI, updated to support pagination.
 */
public record Shop(
        String id,
        String title,
        int size,
        Map<String, PaginationItem> paginationItems, // Holds next_page, previous_page, filler
        List<ShopItem> items, // All possible items
        String type, // "rotational" or null
        String rotationInterval, // e.g. "24h" or null
        int slots, // Number of items to show per rotation (0 for non-rotational)
        List<ShopItem> activeItems, // Items currently active in this rotation (null for non-rotational)
        List<Integer> itemSlots, // Slots for rotational shop items (null for non-rotational)
        List<ShopItem> featuredItems, // Always-present featured items (null for non-rotational)
        List<Integer> featuredSlots // Slots for featured items (null for non-rotational)
) {}
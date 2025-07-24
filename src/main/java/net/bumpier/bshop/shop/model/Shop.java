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
        List<ShopItem> items // Changed from a Map to a List
) {}
package net.bumpier.bshop.shop.ui;

/**
 * Holds the state of a player's currently viewed shop page.
 */
public record PageInfo(
        String shopId,
        int currentPage
) {}
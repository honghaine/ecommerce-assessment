package com.flashsale.inventory.service;

public interface InventoryService {

    /** Opens stock for a new product. */
    void open(long productId, String region, int quantity);

    /**
     * Allocates stock to a flash-sale quota ({@code available → reserved}).
     *
     * @return false if not enough available stock
     */
    boolean reserve(long productId, String region, int quantity);

    void restock(long productId, String region, int quantity);
}

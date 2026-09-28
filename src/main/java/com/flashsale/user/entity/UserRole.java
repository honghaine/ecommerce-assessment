package com.flashsale.user.entity;

/** One role per account. */
public enum UserRole {
    /** Buyer. */
    USER,
    /** Shop owner: owns products, nominates them into flash-sale slots. */
    SELLER,
    /** Platform operator: manages flash-sale slots of their region. */
    PLATFORM_ADMIN
}

package com.flashsale.flashsale.entity;

/** Whether a slot is live is decided by its time window; status only matters for CANCELLED. */
public enum SessionStatus {
    SCHEDULED,
    ACTIVE,
    ENDED,
    CANCELLED
}

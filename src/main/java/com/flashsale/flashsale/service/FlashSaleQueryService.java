package com.flashsale.flashsale.service;

import com.flashsale.flashsale.dto.CurrentFlashSaleResponse;

public interface FlashSaleQueryService {

    /** Products on flash sale right now in the region, grouped by slot. */
    CurrentFlashSaleResponse current(String region);
}

package com.flashsale.flashsale.service;

import java.time.LocalDate;
import java.util.List;

import com.flashsale.flashsale.dto.AdminSessionView;
import com.flashsale.flashsale.dto.FlashSaleConfigRequest;
import com.flashsale.flashsale.dto.FlashSaleConfigResponse;
import com.flashsale.flashsale.model.GenerationResult;

/** Platform-admin view and control of a region's flash-sale schedule. */
public interface PlatformFlashSaleService {

    FlashSaleConfigResponse getConfig(String region);

    FlashSaleConfigResponse updateConfig(String region, long adminId, FlashSaleConfigRequest request);

    /** Slots of a region-local day with all sellers' items grouped by seller. */
    List<AdminSessionView> listSessions(String region, LocalDate date);

    GenerationResult generateNow(String region);
}

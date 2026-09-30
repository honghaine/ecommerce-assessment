package com.flashsale.flashsale.service;

import java.time.LocalDate;
import java.util.List;

import com.flashsale.flashsale.dto.RuleRequest;
import com.flashsale.flashsale.dto.RuleResponse;
import com.flashsale.flashsale.dto.SellerItemResponse;
import com.flashsale.flashsale.entity.RuleStatus;

/** Seller's recurring flash-sale rules and the slot items generated from them. Own data only. */
public interface SellerFlashSaleService {

    RuleResponse createRule(long sellerId, String region, RuleRequest request);

    List<RuleResponse> listRules(long sellerId);

    /** Removes the rule's not-yet-started items (stock returned), applies the change, regenerates. */
    RuleResponse updateRule(long sellerId, long ruleId, RuleRequest request);

    /** PAUSED / ARCHIVED remove future items (stock returned); ACTIVE regenerates them. */
    RuleResponse changeStatus(long sellerId, long ruleId, RuleStatus status);

    List<SellerItemResponse> listItems(long sellerId, String region, LocalDate date);

    /** Skips one occurrence before its slot starts; its reserved quota goes back to available stock. */
    SellerItemResponse withdrawItem(long sellerId, long itemId);
}

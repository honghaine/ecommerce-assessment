package com.flashsale.flashsale.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flashsale.flashsale.service.SlotGenerationService;
import com.flashsale.region.service.RegionService;

/**
 * Keeps slots/items generated for the configured horizon in every region. Safe on every instance:
 * the generator serializes per region with a Postgres advisory lock and is idempotent.
 */
@Slf4j
@Component
public class SlotGenerationScheduler {

    private final SlotGenerationService generator;
    private final RegionService regionService;

    public SlotGenerationScheduler(SlotGenerationService generator, RegionService regionService) {
        this.generator = generator;
        this.regionService = regionService;
    }

    @Scheduled(fixedDelayString = "${app.flash-sale.generate-interval-ms:600000}",
            initialDelayString = "${app.flash-sale.generate-initial-delay-ms:10000}")
    public void generateAll() {
        for (String region : regionService.codes()) {
            try {
                generator.generate(region);
            } catch (RuntimeException ex) {
                log.warn("Flash sale generation failed for {} (will retry)", region, ex);
            }
        }
    }
}

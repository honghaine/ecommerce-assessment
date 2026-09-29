package com.flashsale.seed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the seeder at startup and hourly (so tomorrow's slots always exist) when
 * {@code app.seed.enabled=true}. Separate bean so calls go through the ShedLock proxy.
 */
@Slf4j
@Component
public class DemoSeedTrigger {

    private final DemoDataSeeder seeder;
    private final SeedProperties properties;

    public DemoSeedTrigger(DemoDataSeeder seeder, SeedProperties properties) {
        this.seeder = seeder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "0 5 * * * *")
    public void hourly() {
        run();
    }

    private void run() {
        if (!properties.enabled()) {
            return;
        }
        try {
            seeder.seed();
        } catch (RuntimeException ex) {
            log.warn("Demo seeding failed (will retry next hour)", ex);
        }
    }
}

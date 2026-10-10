package com.github.svenfran.budgetapp.budgetappbackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aktiviert die @Scheduled-Jobs (Recurring Carts, Token-Cleanup, Health-Heartbeat).
 * Im Testprofil über app.scheduling.enabled=false abgeschaltet; ohne Property (dev, prod) aktiv.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

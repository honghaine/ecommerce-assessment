package com.flashsale.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    /**
     * Fail fast while Redis is disconnected (instead of queueing commands until the timeout), so fail-open checks
     * and the DB fallback react immediately; the client keeps reconnecting in the background.
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer failFastWhenDisconnected() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }
}

package com.sos.club.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {

    /** Pool dedie au dispatch immediat des events apres commit. */
    @Bean(name = "eventExecutor")
    public ThreadPoolTaskExecutor eventExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("event-");
        ex.initialize();
        return ex;
    }
}

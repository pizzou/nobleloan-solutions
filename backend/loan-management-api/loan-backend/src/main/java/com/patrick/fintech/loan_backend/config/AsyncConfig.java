package com.patrick.fintech.loan_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "loansaasAsyncExecutor")
    public Executor loansaasAsyncExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        /*
         * Number of threads available for asynchronous work.
         *
         * Webhooks are external HTTP calls, so we do not want
         * them executing on the payment request thread.
         */
        executor.setCorePoolSize(4);

        /*
         * Maximum number of concurrent asynchronous tasks.
         */
        executor.setMaxPoolSize(12);

        /*
         * Tasks waiting for an available worker thread.
         */
        executor.setQueueCapacity(100);

        /*
         * Makes thread names easy to identify in production logs.
         */
        executor.setThreadNamePrefix(
                "loansaas-async-"
        );

        /*
         * Finish already-submitted tasks during graceful
         * application shutdown.
         */
        executor.setWaitForTasksToCompleteOnShutdown(
                true
        );

        /*
         * Give existing asynchronous tasks time to finish
         * during shutdown.
         */
        executor.setAwaitTerminationSeconds(
                30
        );

        /*
         * Initialize the executor.
         */
        executor.initialize();

        return executor;
    }
}
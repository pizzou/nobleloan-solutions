package com.patrick.fintech.loan_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executors used by the application for work that must never block an HTTP
 * request, including historical loan imports.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

        @Bean(name = "loansaasAsyncExecutor")
        public Executor loansaasAsyncExecutor() {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

                // Legacy imports are database-heavy. Keep concurrency bounded so a
                // large import cannot exhaust the datasource connection pool.
                executor.setCorePoolSize(2);
                executor.setMaxPoolSize(4);
                executor.setQueueCapacity(25);
                executor.setThreadNamePrefix("loansaas-async-");

                // Never execute a long import on the HTTP request thread when the
                // executor is saturated. Reject it instead; the controller can report
                // that the job could not be queued.
                executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

                executor.setWaitForTasksToCompleteOnShutdown(true);
                executor.setAwaitTerminationSeconds(60);
                executor.setAllowCoreThreadTimeOut(false);
                executor.initialize();

                return executor;
        }

        /**
         * Dedicated executor for external email delivery.
         *
         * Login OTP delivery must never wait behind legacy-import, reconciliation,
         * or audit tasks. A small bounded pool is sufficient because each task is
         * an outbound HTTPS request and MailService has its own network timeout.
         */
        @Bean(name = "mailAsyncExecutor")
        public Executor mailAsyncExecutor() {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(1);
                executor.setMaxPoolSize(2);
                executor.setQueueCapacity(50);
                executor.setThreadNamePrefix("loansaas-mail-");
                executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
                executor.setWaitForTasksToCompleteOnShutdown(true);
                executor.setAwaitTerminationSeconds(20);
                executor.setAllowCoreThreadTimeOut(false);
                executor.initialize();
                return executor;
        }
}
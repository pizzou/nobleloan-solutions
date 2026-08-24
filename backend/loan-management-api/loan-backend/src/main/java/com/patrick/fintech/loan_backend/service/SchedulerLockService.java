package com.patrick.fintech.loan_backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
public class SchedulerLockService {

        @PersistenceContext
        private EntityManager em;

        private final TransactionTemplate transactionTemplate;

        public SchedulerLockService(
                        PlatformTransactionManager transactionManager) {

                this.transactionTemplate = new TransactionTemplate(transactionManager);

                this.transactionTemplate.setReadOnly(false);
        }

        /**
         * Attempts to acquire a distributed scheduler lock.
         *
         * The database operation is ALWAYS executed inside an explicit
         * transaction.
         *
         * This deliberately uses TransactionTemplate instead of relying
         * on @Transactional self-invocation. That prevents the production
         * TransactionRequiredException that occurs when runExclusively()
         * calls tryAcquire() from inside this same bean.
         *
         * PostgreSQL performs the acquisition atomically:
         *
         * 1. Missing lock -> INSERT.
         * 2. Expired lock -> UPDATE.
         * 3. Active lock -> no change.
         */
        public boolean tryAcquire(
                        String jobName,
                        Duration lockFor) {

                validate(jobName, lockFor);

                Boolean acquired = transactionTemplate.execute(status -> {

                        LocalDateTime now = LocalDateTime.now();

                        LocalDateTime lockedUntil = now.plus(lockFor);

                        return tryAcquireInTransaction(
                                        jobName,
                                        now,
                                        lockedUntil);
                });

                boolean result = Boolean.TRUE.equals(acquired);

                if (result) {

                        log.debug(
                                        "[SchedulerLock] Acquired lock '{}' for {}",
                                        jobName,
                                        lockFor);

                } else {

                        log.info(
                                        "[SchedulerLock] Lock '{}' is already held by another instance - skipping",
                                        jobName);
                }

                return result;
        }

        /**
         * Releases a scheduler lock.
         *
         * The DELETE/UPDATE is executed inside its own transaction.
         */
        public void release(
                        String jobName) {

                if (jobName == null || jobName.isBlank()) {
                        return;
                }

                transactionTemplate.executeWithoutResult(status -> {

                        LocalDateTime now = LocalDateTime.now();

                        int affected = em.createNativeQuery("""
                                        UPDATE scheduler_locks
                                        SET locked_until = :now
                                        WHERE job_name = :jobName
                                        """)
                                        .setParameter(
                                                        "now",
                                                        now)
                                        .setParameter(
                                                        "jobName",
                                                        jobName)
                                        .executeUpdate();

                        if (affected == 1) {

                                log.debug(
                                                "[SchedulerLock] Released lock '{}'",
                                                jobName);

                        } else {

                                log.debug(
                                                "[SchedulerLock] Lock '{}' did not exist during release",
                                                jobName);
                        }
                });
        }

        /**
         * Executes a scheduled job exclusively across all application
         * instances sharing the same PostgreSQL database.
         *
         * The lock transaction is deliberately completed BEFORE the
         * business job starts.
         *
         * This is important:
         *
         * - no long-running reconciliation transaction
         * - no database transaction held while sending notifications
         * - no database transaction held while iterating organizations
         * - no transaction held for the entire scheduled job
         */
        public void runExclusively(
                        String jobName,
                        Duration lockFor,
                        Runnable job) {

                if (job == null) {
                        throw new IllegalArgumentException(
                                        "Scheduled job must not be null");
                }

                boolean acquired;

                try {

                        acquired = tryAcquire(
                                        jobName,
                                        lockFor);

                } catch (Exception acquisitionError) {

                        log.error(
                                        "[SchedulerLock] Failed to acquire lock '{}'. Scheduled job will not execute.",
                                        jobName,
                                        acquisitionError);

                        return;
                }

                if (!acquired) {

                        log.info(
                                        "[Scheduler] '{}' is already running on another instance - skipping",
                                        jobName);

                        return;
                }

                try {

                        job.run();

                } catch (Throwable jobError) {

                        /*
                         * Never allow a scheduler exception to escape into
                         * Spring's ScheduledMethodRunnable and become an
                         * "Unexpected error occurred in scheduled task".
                         *
                         * The actual exception is still logged in full.
                         */
                        log.error(
                                        "[Scheduler] Job '{}' failed.",
                                        jobName,
                                        jobError);

                } finally {

                        try {

                                release(jobName);

                        } catch (Exception releaseError) {

                                /*
                                 * Lock release failure is operationally important,
                                 * but must not crash the scheduler thread.
                                 *
                                 * The lock has a finite expiration time, so the
                                 * next execution can recover automatically after
                                 * expiry.
                                 */
                                log.error(
                                                "[SchedulerLock] Failed to release lock '{}'. The lock will expire automatically after its configured duration.",
                                                jobName,
                                                releaseError);
                        }
                }
        }

        /**
         * Performs the PostgreSQL UPSERT while a transaction is active.
         */
        private boolean tryAcquireInTransaction(
                        String jobName,
                        LocalDateTime now,
                        LocalDateTime lockedUntil) {

                String sql = """
                                INSERT INTO scheduler_locks (
                                    job_name,
                                    locked_until
                                )
                                VALUES (
                                    :jobName,
                                    :lockedUntil
                                )
                                ON CONFLICT (job_name)
                                DO UPDATE SET
                                    locked_until = EXCLUDED.locked_until
                                WHERE scheduler_locks.locked_until < :now
                                """;

                int affected = em.createNativeQuery(sql)
                                .setParameter(
                                                "jobName",
                                                jobName)
                                .setParameter(
                                                "lockedUntil",
                                                lockedUntil)
                                .setParameter(
                                                "now",
                                                now)
                                .executeUpdate();

                return affected == 1;
        }

        private void validate(
                        String jobName,
                        Duration lockFor) {

                if (jobName == null || jobName.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Scheduler job name is required");
                }

                if (jobName.length() > 100) {

                        throw new IllegalArgumentException(
                                        "Scheduler job name must not exceed 100 characters");
                }

                if (lockFor == null
                                || lockFor.isNegative()
                                || lockFor.isZero()) {

                        throw new IllegalArgumentException(
                                        "Scheduler lock duration must be greater than zero");
                }
        }
}
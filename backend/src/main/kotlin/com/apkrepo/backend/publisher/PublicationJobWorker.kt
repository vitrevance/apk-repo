package com.apkrepo.backend.publisher

import com.apkrepo.backend.domain.PublicationJob
import com.apkrepo.backend.domain.PublicationJobState
import com.apkrepo.backend.domain.ReleaseStatus
import com.apkrepo.backend.repository.PublicationJobRepository
import com.apkrepo.backend.repository.ReleaseRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Serial publication-job worker: claims the oldest PENDING job, runs the
 * full repository regeneration via [FdroidPublisher] and flips job and
 * release states transactionally. The poller uses fixedDelay so runs never
 * overlap, and the bean only exists when the publisher is enabled.
 *
 * Failure semantics: a failed job is recorded with the fdroidserver
 * output; releases stay READY (no partially-published state is visible)
 * and the next successful job heals the repository.
 *
 * Crash recovery: at startup, jobs stuck in RUNNING (a JVM died mid-run)
 * are reset to PENDING and retried up to [MAX_ATTEMPTS] times.
 */
@Component
@ConditionalOnProperty(
    prefix = "apk-repo.publisher",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PublicationJobWorker(
    private val jobRepository: PublicationJobRepository,
    private val releaseRepository: ReleaseRepository,
    private val publisher: FdroidPublisher,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(PublicationJobWorker::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Claims and processes jobs until none are PENDING. fixedDelay means
     * the next poll starts only after this run returns, so processing is
     * strictly serial.
     */
    @Scheduled(fixedDelayString = "\${apk-repo.publisher.poll-interval-ms:2000}")
    fun pollOnce() {
        while (true) {
            val job = claimNextJob() ?: return
            process(job)
        }
    }

    /** Recover interrupted jobs and immediately drain the queue at startup. */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        recoverInterruptedJobs()
        pollOnce()
    }

    private fun claimNextJob(): PublicationJob? = transactionTemplate.execute {
        val job = jobRepository.findFirstByStateOrderByCreatedAtAsc(PublicationJobState.PENDING)
            ?: return@execute null
        job.state = PublicationJobState.RUNNING
        job.startedAt = Instant.now()
        job.generationId = "gen-${System.currentTimeMillis()}"
        jobRepository.save(job)
    }

    private fun process(job: PublicationJob) {
        try {
            val outcome = publisher.publishOnce(job)
            transactionTemplate.execute {
                val done = jobRepository.findById(job.id).orElse(null) ?: return@execute
                done.state = PublicationJobState.COMPLETED
                done.finishedAt = Instant.now()
                jobRepository.save(done)

                // Flip only what is still READY: an admin may have
                // unpublished a release while this job was running.
                outcome.publishedReleaseIds.forEach { releaseId ->
                    releaseRepository.findById(releaseId).ifPresent { release ->
                        if (release.status == ReleaseStatus.READY) {
                            release.status = ReleaseStatus.PUBLISHED
                            release.publishedAt = Instant.now()
                            releaseRepository.save(release)
                        }
                    }
                }
            }
            log.info(
                "Publication job {} completed: {} release(s) in repository, fingerprint={}",
                job.id, outcome.publishedReleaseIds.size, outcome.fingerprint,
            )
        } catch (e: Exception) {
            log.error("Publication job {} failed", job.id, e)
            val detail = if (e is PublisherException) {
                e.message + (e.cliOutput?.let { "\n${it.takeLast(4000)}" } ?: "")
            } else {
                e.toString()
            }
            transactionTemplate.execute {
                val failed = jobRepository.findById(job.id).orElse(null) ?: return@execute
                failed.state = PublicationJobState.FAILED
                failed.finishedAt = Instant.now()
                failed.errorMessage = detail
                jobRepository.save(failed)
            }
        }
    }

    private fun recoverInterruptedJobs() {
        transactionTemplate.execute {
            jobRepository.findByState(PublicationJobState.RUNNING).forEach { job ->
                job.retryCount += 1
                if (job.retryCount >= MAX_ATTEMPTS) {
                    job.state = PublicationJobState.FAILED
                    job.finishedAt = Instant.now()
                    job.errorMessage =
                        "Interrupted by a restart; gave up after $MAX_ATTEMPTS attempts."
                } else {
                    job.state = PublicationJobState.PENDING
                }
                jobRepository.save(job)
            }
        }
    }

    private companion object {
        private const val MAX_ATTEMPTS = 3
    }
}

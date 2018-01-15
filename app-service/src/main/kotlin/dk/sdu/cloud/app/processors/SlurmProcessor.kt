package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.StorageConfiguration
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.HPCAppEvent
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.scpDownload
import dk.sdu.cloud.app.services.ssh.stat
import dk.sdu.cloud.app.util.BashEscaper
import dk.sdu.cloud.app.util.BashEscaper.safeBashArgument
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.tus.api.CreationCommand
import dk.sdu.cloud.tus.api.TusDescriptions
import dk.sdu.cloud.tus.api.internal.start
import dk.sdu.cloud.tus.api.internal.uploader
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Processes events from slurm
 */
class SlurmProcessor(
        private val cloud: RefreshingJWTAuthenticator,
        private val sshPool: SSHConnectionPool,
        private val irodsConfig: StorageConfiguration,
        private val slurmAgent: SlurmPollAgent,
        private val streamService: HPCStreamService
) {
    // Handles an event captured by te internal SlurmPollAgent and handles it. This will output
    // an event into Kafka once the entire thing has been handled. From this we create a Kafka event.
    private suspend fun handle(event: SlurmEvent): Pair<String, HPCAppEvent> =
            when (event) {
                is SlurmEventBegan -> {
                    val key = transaction { JobsDAO.findSystemIdFromSlurmId(event.jobId) }!!
                    val appEvent = HPCAppEvent.Started(event.jobId)

                    Pair(key, appEvent)
                }

                is SlurmEventEnded -> handleEndedEvent(event)

                else -> throw IllegalStateException()
            }

    private suspend fun handleEndedEvent(event: SlurmEventEnded): Pair<String, HPCAppEvent.Ended> {
        val key = transaction { JobsDAO.findSystemIdFromSlurmId(event.jobId) }!!

        val (jobWithStatus, app) = transaction {
            val jobWithStatus = JobsDAO.findJobWithStatusBySlurmId(event.jobId)!!
            val app = ApplicationDAO.findByNameAndVersion(jobWithStatus.appName, jobWithStatus.appVersion)!!

            Pair(jobWithStatus, app)
        }
        val appParameters = jobWithStatus.parameters

        // TODO We need to be able to resolve this in a uniform manner. Since we will need these in multiple places.
        // There will also be some logic in handling optional parameters.
        val outputs = app.parameters
                .filterIsInstance<ApplicationParameter.OutputFile>()
                .map { it.map(appParameters[it.name]) }

        return sshPool.use {
            log.info("Handling Slurm ended event! $key ${event.jobId}")
            // Transfer output files
            for (transfer in outputs) {
                log.debug("Transferring file: $transfer")
                val workingDirectory = URI(jobWithStatus.workingDirectory)
                val source = workingDirectory.resolve(transfer.source)

                if (!source.path.startsWith(workingDirectory.path)) {
                    log.warn("File ${transfer.source} did not resolve to be within working directory " +
                            "($source versus $workingDirectory). Skipping this file")
                    continue
                }

                log.debug("Looking for file at ${source.path}")
                val sourceFile = stat(source.path)
                log.debug("Got back: $sourceFile")

                if (sourceFile == null) {
                    log.info("Could not find output file at: ${source.path}. Skipping file")
                    continue
                }

                if (sourceFile.isDir) {
                    log.debug("Source file is a directory. Zipping it up")
                    val zipPath = source.path + ".zip"
                    val (status, output) = execWithOutputAsText("zip -r " +
                            BashEscaper.safeBashArgument(zipPath) + " " +
                            BashEscaper.safeBashArgument(source.path))

                    if (status != 0) {
                        log.warn("Unable to create zip archive of output!")
                        log.warn("Path: ${source.path}")
                        log.warn("Status: $status")
                        log.warn("Output: $output")

                        return@use Pair(key, HPCAppEvent.UnsuccessfullyCompleted)
                    }

                    TODO("Handle directory uploads")
                } else {
                    log.debug("Downloading file from ${source.path}")

                    // TODO Upload here
                    val upload = runBlocking {
                        val owner = jobWithStatus.jobInfo.owner
                        TusDescriptions.create.call(CreationCommand(
                                fileName = jobWithStatus.jobInfo.jobId + "-" + transfer.source,
                                owner = owner,
                                location = "/${irodsConfig.zone}/home/$owner/Jobs",
                                length = sourceFile.size,
                                sensitive = false // TODO Sensitivity
                        ), cloud)
                    } as? RESTResponse.Ok ?: throw IllegalStateException("Upload failed")
                    val uploadLocation = upload.response.headers["Location"]!!


                    log.debug("Upload target is: $uploadLocation")
                    // TODO Refactor upload client to allow us to find a suitable target

                    if (sourceFile.size >= Int.MAX_VALUE) {
                        log.warn("sourceFile.size (${sourceFile.size}) >= Int.MAX_VALUE. Currently not supported")
                        throw IllegalStateException("sourceFile.size >= Int.MAX_VALUE")
                    }

                    scpDownload(source.path) {
                        TusDescriptions.uploader(it, uploadLocation, sourceFile.size.toInt(), cloud).start {
                            log.debug("${jobWithStatus.jobInfo.jobId}: $it/${sourceFile.size} bytes transferred")
                        }
                    }
                }
            }

            // TODO Crashing after deletion but before we send event will cause a lot of problems. We should split
            // this into two.
            log.debug("Deleting job directory")
            execWithOutputAsText("rm -rf ${safeBashArgument(jobWithStatus.jobDirectory)}")

            log.debug("Successfully completed job")
            Pair(key, HPCAppEvent.SuccessfullyCompleted(event.jobId))
        }
    }

    fun init() {
        slurmAgent.addListener {
            runBlocking {
                val (key, event) = handle(it)
                streamService.appEventsProducer.emit(key, event)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SlurmProcessor::class.java)
    }
}
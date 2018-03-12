package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.StorageConfiguration
import dk.sdu.cloud.app.api.FileTransferDescription
import dk.sdu.cloud.app.api.HPCAppEvent
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.lsWithGlob
import dk.sdu.cloud.app.services.ssh.scpDownload
import dk.sdu.cloud.app.services.ssh.stat
import dk.sdu.cloud.app.util.BashEscaper
import dk.sdu.cloud.app.util.BashEscaper.safeBashArgument
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.EventProducer
import dk.sdu.cloud.storage.api.CreateDirectoryRequest
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.tus.api.CreationCommand
import dk.sdu.cloud.tus.api.TusDescriptions
import dk.sdu.cloud.tus.api.internal.start
import dk.sdu.cloud.tus.api.internal.uploader
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

/**
 * Processes events from slurm
 */
class SlurmEventProcessor(
    private val cloud: RefreshingJWTAuthenticator,
    private val sshPool: SSHConnectionPool,
    private val irodsConfig: StorageConfiguration,
    private val slurmAgent: SlurmPollAgent,
    private val appEventProducer: EventProducer<String, HPCAppEvent>,
    private val jobService: JobService
) {
    // Handles an event captured by te internal SlurmPollAgent and handles it. This will output
    // an event into Kafka once the entire thing has been handled. From this we create a Kafka event.
    private suspend fun handle(event: SlurmEvent): Pair<String, HPCAppEvent>? {
        log.debug("handle($event)")
        return when (event) {
            is SlurmEventBegan -> {
                val key = jobService.findSystemIdFromSlurmId(event.jobId)!!
                val appEvent = HPCAppEvent.Started(event.jobId)

                Pair(key, appEvent)
            }

            is SlurmEventFailed -> {
                val key = jobService.findSystemIdFromSlurmId(event.jobId) ?: return null
                Pair(key, HPCAppEvent.UnsuccessfullyCompleted)
            }

            is SlurmEventEnded -> handleEndedEvent(event)
        }.also {
            log.debug("handle() returning $it")
        }
    }

    // TODO Refactor and improve testability situation
    private suspend fun handleEndedEvent(event: SlurmEventEnded): Pair<String, HPCAppEvent.Ended> {
        val key = jobService.findSystemIdFromSlurmId(event.jobId)!!

        val (jobWithStatus, app) = run {
            val jobWithStatus = jobService.findJobWithStatusBySlurmId(event.jobId)!!
            val app = ApplicationDAO.findByNameAndVersion(jobWithStatus.appName, jobWithStatus.appVersion)!!

            Pair(jobWithStatus, app)
        }

        return sshPool.use {
            val owner = jobWithStatus.jobInfo.owner
            val jobId = jobWithStatus.jobInfo.jobId
            val outputDirectoryWithoutZone = "/home/$owner/Jobs/$jobId"
            val outputDirectory = "/${irodsConfig.zone}$outputDirectoryWithoutZone"

            log.info("Handling Slurm ended event! $key ${event.jobId}")

            log.debug("Locating output files")
            val outputs = app.outputFileGlobs
                .flatMap {
                    lsWithGlob(jobWithStatus.workingDirectory, it)
                }
                .map {
                    val file = File(it.first)
                    FileTransferDescription(file.absolutePath, file.name)
                }
            log.debug("Found: $outputs")

            run {
                log.debug("Creating directory...")
                val directoryCreation = runBlocking {
                    FileDescriptions.createDirectory.call(
                        CreateDirectoryRequest(
                            outputDirectoryWithoutZone, owner
                        ), cloud
                    )
                }

                if (directoryCreation !is RESTResponse.Ok) {
                    log.warn("Unable to create directory: $directoryCreation")
                    return@use Pair(key, HPCAppEvent.UnsuccessfullyCompleted)
                } else {
                    log.debug("Directory created successfully")
                }
            }

            // Transfer output files
            for (transfer in outputs) {
                log.debug("Transferring file: $transfer")
                val workingDirectory = URI(jobWithStatus.workingDirectory)
                val source = workingDirectory.resolve(transfer.source)

                if (!source.path.startsWith(workingDirectory.path)) {
                    log.warn(
                        "File ${transfer.source} did not resolve to be within working directory " +
                                "($source versus $workingDirectory). Skipping this file"
                    )
                    continue
                }

                log.debug("Looking for file at ${source.path}")
                val sourceFile = stat(source.path)
                log.debug("Got back: $sourceFile")

                if (sourceFile == null) {
                    log.info("Could not find output file at: ${source.path}. Skipping file")
                    continue
                }

                val (fileToTransferFromHPC, fileToTransferSize) = if (!sourceFile.isDir) {
                    Pair(source.path, sourceFile.size)
                } else {
                    log.debug("Source file is a directory. Zipping it up")
                    val zipPath = source.path + ".zip"
                    val (status, output) = execWithOutputAsText(
                        "zip -r " +
                                BashEscaper.safeBashArgument(zipPath) + " " +
                                BashEscaper.safeBashArgument(source.path)
                    )

                    if (status != 0) {
                        log.warn("Unable to create zip archive of output!")
                        log.warn("Path: ${source.path}")
                        log.warn("Status: $status")
                        log.warn("Output: $output")

                        return@use Pair(key, HPCAppEvent.UnsuccessfullyCompleted)
                    }

                    val zipStat = stat(zipPath) ?: return@use run {
                        log.warn("Unable to find zip file after creation. Expected it at: $zipPath")
                        Pair(key, HPCAppEvent.UnsuccessfullyCompleted)
                    }

                    Pair(zipPath, zipStat.size)
                }

                log.debug("Downloading file from $fileToTransferFromHPC")

                val upload = runBlocking {
                    val payload = CreationCommand(
                        fileName = transfer.destination,
                        owner = owner,
                        location = outputDirectory,
                        length = fileToTransferSize,
                        sensitive = false // TODO Sensitivity
                    )
                    log.debug("Upload to create at SDUCloud: $payload")
                    TusDescriptions.create.call(payload, cloud)
                } as? RESTResponse.Ok ?: throw IllegalStateException("Upload failed")

                val uploadLocation = upload.response.headers["Location"]!!
                log.debug("Upload target is: $uploadLocation")

                if (sourceFile.size >= Int.MAX_VALUE) {
                    log.warn("sourceFile.size (${sourceFile.size}) >= Int.MAX_VALUE. Currently not supported")
                    return@use Pair(key, HPCAppEvent.UnsuccessfullyCompleted)
                }

                scpDownload(source.path) {
                    TusDescriptions.uploader(it, uploadLocation, sourceFile.size.toInt(), cloud).start {
                        log.debug("$jobId: $it/${sourceFile.size} bytes transferred")
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
                val (key, event) = handle(it) ?: return@runBlocking
                appEventProducer.emit(key, event)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SlurmEventProcessor::class.java)
    }
}
package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.ExportedParameters
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.files
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.PathLike
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.child
import dk.sdu.cloud.plugins.normalize
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.prettyMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable

/**
 * A plugin which mounts user-input into the containers
 */
class FeatureFileMount(
    private val fs: NativeFS,
    private val memberFiles: MemberFiles,
    private val pathConverter: PathConverter,
    private val limitChecker: LimitChecker,
) : JobFeature {
    private suspend fun JobManagement.findJobFolder(job: Job, initializeFolder: Boolean): InternalFile {
        val username = job.owner.createdBy
        val project = job.owner.project

        val jobResources = resources.findResources(job)
        val internalHome = memberFiles.initializeMemberFiles(username, project)
        val file = internalHome
            .child(JOBS_FOLDER)
            .child(jobResources.application.metadata.title)
            .child(
                if (job.specification.name != null) {
                    "${job.specification.name} (${job.id})"
                } else {
                    job.id
                }
            )

        if (initializeFolder) {
            try {
                fs.createDirectories(file)
            } catch (ex: FSException.NotFound) {
                log.warn("Unable to create directory, needed for file mounts: $file")
                throw ex
            } catch (ex: FSException.AlreadyExists) {
                // Ignored
            }

            val jobParameterJson = job.status.jobParametersJson
            if (jobParameterJson != null) {
                val jobParamsFile = InternalFile(
                    joinPath(
                        file.path.removeSuffix("/"),
                        "JobParameters.json"
                    ).removeSuffix("/")
                )
                try {
                    fs.openForWriting(jobParamsFile, WriteConflictPolicy.RENAME).second.bufferedWriter().use {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        it.write(prettyMapper.encodeToString(ExportedParameters.serializer(), jobParameterJson))
                    }
                } catch (ex: Throwable) {
                    log.warn("Unable to create JobParameters.json for job: ${job.id} ${jobParamsFile}. ${ex.stackTraceToString()}")
                }
            }
        }

        return file
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        data class FileMount(val system: FsSystem, val path: String, val readOnly: Boolean) {
            val fileName = path.normalize().fileName()
        }

        val fileCollections = job.files.map {
            it.path.normalize().components().getOrNull(0) ?: error("Unexpected path: $it")
        }

        for (coll in fileCollections) {
            limitChecker.checkLimit(coll)
        }

        val fileMounts = run {
            val allMounts = job.files.map {
                val internalFile = ucloudToRelative(UCloudFile.create(it.path))
                FileMount(internalFile.system, internalFile.relativePath, it.readOnly)
            }

            allMounts.associateBy { it.path }.values
        }

        val jobFolder = findJobFolder(job, initializeFolder = true)
        val relativeJobFolder = internalToRelative(jobFolder)
        val ucloudJobFolder = pathConverter.internalToUCloud(jobFolder)

        val jobFolderCollection = ucloudJobFolder.path.components().getOrNull(0)
            ?: error("Unexpected job folder: $ucloudJobFolder")
        limitChecker.checkLimit(jobFolderCollection)

        JobsControl.update.call(
            bulkRequestOf(
                ResourceUpdateAndId(
                    job.id,
                    JobUpdate(outputFolder = ucloudJobFolder.path)
                )
            ),
            k8.serviceClient
        ).orRethrowAs { throw RPCException("Internal error - Could not add output folder", HttpStatusCode.BadGateway) }

        builder.mountUCloudFileSystem(
            relativeJobFolder.system,
            relativeJobFolder.normalize().path.removePrefix("/"),
            "/work",
            readOnly = false
        )

        fileMounts.forEach { mount ->
            builder.mountUCloudFileSystem(
                mount.system,
                mount.path.normalize().removePrefix("/"),
                joinPath("/work", mount.fileName),
                readOnly = mount.readOnly,
            )
        }
    }

    override suspend fun JobManagement.onJobComplete(rootJob: Container, children: List<Container>) {
        val workMount = findWorkMount(rootJob) ?: return
        val subFolders = findSubMountNames(rootJob)
        if (subFolders.isEmpty()) return

        for (folder in subFolders) {
            val file = relativeToInternal(workMount.child(folder))

            try {
                val internalStat = fs.stat(file)
                if (internalStat.fileType == FileType.DIRECTORY) {
                    fs.delete(file, allowRecursion = false)
                }
            } catch (ex: FSException.NotFound) {
                // Ignored
            } catch (ex: Throwable) {
                log.info(
                    "Caught exception while cleaning up empty mount directories for:" +
                            "\n\tjob = ${rootJob.jobId}" +
                            "\n\tmountedDirectory=$file"
                )
                log.info(ex.stackTraceToString())
            }
        }
    }

    suspend fun JobManagement.findWorkMount(jobFromServer: Container): SystemRelativeFile? {
        val cachedJob = k8.jobCache.findJob(jobFromServer.jobId) ?: return null
        return internalToRelative(findJobFolder(cachedJob, initializeFolder = false))
    }

    private suspend fun JobManagement.findSubMountNames(rootJob: Container): List<String> {
        val cachedJob = k8.jobCache.findJob(rootJob.jobId) ?: return emptyList()
        return cachedJob.files.map { param ->
            param.path.normalize().fileName()
        }
    }

    suspend fun internalToRelative(file: InternalFile): SystemRelativeFile {
        val (_, system, maintenance) = pathConverter.locator.resolveDriveByInternalFile(file)
        if (maintenance) {
            throw RPCException(
                "One of your requested drives is currently in maintenance. Try again later.",
                HttpStatusCode.BadRequest,
                "MAINTENANCE"
            )
        }

        val relativePath = file.path.removeSuffix(system.mountPath.removeSuffix("/") + "/")
        return SystemRelativeFile(
            system,
            relativePath
        )
    }

    suspend fun ucloudToRelative(file: UCloudFile): SystemRelativeFile {
        return internalToRelative(pathConverter.ucloudToInternal(file))
    }

    fun relativeToInternal(relativeFile: SystemRelativeFile): InternalFile {
        // NOTE(Dan): It becomes very apparent here that we absolutely most not run jobs while we are changing which
        // system a drive belongs to. The functions feeding into this will simply return the current location of
        // the drive, not the location of the drive when the job started. This is why we must always shut down all
        // jobs when entering maintenance mode, and keep all jobs off while we are in maintenance mode.
        return InternalFile(relativeFile.system.mountPath.removeSuffix("/") + "/" + relativeFile.relativePath)
    }

    companion object : Loggable {
        const val JOBS_FOLDER = "Jobs"

        override val log = logger()
    }
}

data class SystemRelativeFile(
    val system: FsSystem,
    val relativePath: String,
) : PathLike<SystemRelativeFile> {
    override val path: String = relativePath
    override fun withNewPath(path: String): SystemRelativeFile = copy(relativePath = path)
}

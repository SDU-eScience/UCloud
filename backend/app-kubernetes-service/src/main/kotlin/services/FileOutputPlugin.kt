package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.file.ucloud.services.NativeFS
import dk.sdu.cloud.file.ucloud.services.PathConverter
import dk.sdu.cloud.file.ucloud.services.RelativeInternalFile

class FileOutputPlugin(
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
    private val logService: K8LogService,
    private val fileMountPlugin: FileMountPlugin,
) : JobManagementPlugin {
    override suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {
        val workMount = fileMountPlugin.findWorkMount(jobFromServer)

        if (workMount != null) {
            val dir = logService.downloadLogsToDirectory(jobId)
            try {
                dir?.listFiles()?.forEach { file ->
                    val outputFile = pathConverter.relativeToInternal(
                        RelativeInternalFile(joinPath(workMount.path.removeSuffix("/"), file.name).removeSuffix("/"))
                    )

                    fs.openForWriting(outputFile, WriteConflictPolicy.RENAME).second.use { outs ->
                        file.inputStream().use { ins ->
                            ins.copyTo(outs)
                        }
                    }
                }
            } finally {
                dir?.deleteRecursively()
            }
        }
    }
}

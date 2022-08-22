package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.RelativeInternalFile
import dk.sdu.cloud.plugins.storage.ucloud.*

class FeatureFileOutput(
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
    private val logService: K8LogService,
    private val fileMountPlugin: FeatureFileMount,
) : JobFeature {
    override suspend fun JobManagement.onJobComplete(rootJob: Container, children: List<Container>) {
        // TODO Download logs
        repeat(10) { println("Download logs") }
        /*
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
         */
    }
}

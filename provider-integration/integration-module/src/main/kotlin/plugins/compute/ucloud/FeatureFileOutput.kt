package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.RelativeInternalFile
import dk.sdu.cloud.plugins.storage.ucloud.*

class FeatureFileOutput(
    private val pathConverter: PathConverter,
    private val fs: NativeFS,
) : JobFeature {
    override suspend fun JobManagement.onJobComplete(rootJob: Container, children: List<Container>) {
        val fileMountPlugin = featureOrNull<FeatureFileMount>() ?: return
        val workMount = with(fileMountPlugin) {
            findWorkMount(rootJob)
        }

        if (workMount != null) {
            children.forEach { child ->
                val outputFile = pathConverter.relativeToInternal(
                    RelativeInternalFile(
                        joinPath(
                            workMount.path.removeSuffix("/"),
                            buildString {
                                append("stdout")
                                if (child.rank != 0) {
                                    append('-')
                                    append(child.rank)
                                }
                                append(".txt")
                            }
                        )
                    )
                )

                runCatching {
                    fs.openForWriting(outputFile, WriteConflictPolicy.REJECT).second.use { outs ->
                        child.downloadLogs(outs)
                    }
                }
            }
        }
    }
}

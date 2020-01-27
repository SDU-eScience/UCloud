package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.client.KubernetesClientException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/**
 * A service responsible for downloading and streaming of K8 logs from user jobs.
 */
class K8LogService(
    private val k8: K8Dependencies
) {
    fun retrieveLogs(requestId: String, startLine: Int, maxLines: Int): Pair<String, Int> {
        return try {
            // This is a stupid implementation that works with the current API. We should be using websockets.
            val pod = k8.nameAllocator.listPods(requestId).firstOrNull() ?: return Pair("", 0)
            val completeLog = k8.nameAllocator.findPodByName(pod.metadata.name).inContainer(USER_CONTAINER).log.lines()
            val lines = completeLog.drop(startLine).take(maxLines)
            val nextLine = startLine + lines.size
            Pair(lines.joinToString("\n"), nextLine)
        } catch (ex: KubernetesClientException) {
            when (ex.status.code) {
                404, 400 -> Pair("", 0)
                else -> throw ex
            }
        }
    }

    fun useLogWatch(requestId: String, block: suspend (Closeable, InputStream) -> Unit): Job {
        val pod = k8.nameAllocator.listPods(requestId).firstOrNull()
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        return k8.scope.launch {
            val res = k8.nameAllocator.findPodByName(pod.metadata.name).inContainer(USER_CONTAINER).watchLog()

            block(res, res.output)
        }
    }

    fun downloadLog(podName: String): File? {
        try {
            log.debug("Downloading log")
            val logFile = Files.createTempFile("log", ".txt").toFile()
            k8.nameAllocator.findPodByName(podName).inContainer(USER_CONTAINER).logReader.use { ins ->
                logFile.writer().use { out ->
                    ins.copyTo(out)
                }
            }

            return logFile
        } catch (ex: KubernetesClientException) {
            // Assume that this is because there is no log to retrieve
            if (ex.code != 400 && ex.code != 404) {
                throw ex
            }

            log.debug("Could not find a log")
            return null
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

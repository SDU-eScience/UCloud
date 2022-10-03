package dk.sdu.cloud.plugins.compute.udocker

import dk.sdu.cloud.file.orchestrator.api.FindByPath
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.storage.posix.posixFilePermissionsFromInt
import dk.sdu.cloud.utils.homeDirectory
import dk.sdu.cloud.utils.startProcess
import dk.sdu.cloud.utils.startProcessAndCollectToString
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files as NioFiles

class UDocker(private val pluginContext: PluginContext) {
    private val installMutex = Mutex()

    fun init() {
        val ipcServer = pluginContext.ipcServerOptional
        if (ipcServer != null) {
            ipcServer.addHandler(UDockerIpc.install.handler { _, request ->
                FindByPath(install())
            })
        }
    }

    suspend fun install(): String {
        if (pluginContext.config.shouldRunServerCode()) {
            val tmpDir = File(System.getProperty("java.io.tmpdir"))
            val udockerDir = File(tmpDir, "ucloud-udocker")
            val udockerTarGz = File(udockerDir, "udocker.tar.gz")
            if (udockerTarGz.exists()) {
                return udockerTarGz.absolutePath
            } else {
                installMutex.withLock {
                    if (udockerDir.exists()) return udockerTarGz.absolutePath
                    udockerDir.mkdir()

                    HttpClient(CIO).use { httpClient ->
                        FileOutputStream(udockerTarGz).use { out ->
                            val response = httpClient.get(downloadUrl)
                            if (!response.status.isSuccess()) {
                                throw IllegalStateException(
                                    "Could not download UDocker! " +
                                            "HTTP status code: ${response.status}"
                                )
                            }
                            response.bodyAsChannel().copyTo(out)
                        }
                    }

                    val udockerTarGzChecksum = computeChecksum(udockerTarGz)
                    if (udockerTarGzChecksum != downloadChecksum) {
                        throw IllegalStateException("Could not download UDocker! " +
                                "Checksum mismatch got $udockerTarGzChecksum but should be $downloadChecksum")
                    }

                    NioFiles.setPosixFilePermissions(
                        udockerDir.toPath(),
                        posixFilePermissionsFromInt("755".toInt(8))
                    )
                }

                return udockerTarGz.absolutePath
            }
        } else {
            val ipcClient = pluginContext.ipcClient
            val baseInstall = File(ipcClient.sendRequest(UDockerIpc.install, Unit).id)
            val homeDir = File(homeDirectory())
            val installDir = File(homeDir, ".ucloud-udocker")

            val udockerTarGz = File(installDir, "udocker.tar.gz")
            val extractedDir = File(installDir, "udocker")
            if (udockerTarGz.exists()) {
                if (computeChecksum(udockerTarGz) != downloadChecksum || !extractedDir.exists()) {
                    installDir.deleteRecursively()
                } else {
                    return extractedDir.absolutePath
                }
            }

            installDir.mkdir()
            NioFiles.copy(baseInstall.toPath(), udockerTarGz.toPath())
            if (computeChecksum(udockerTarGz) != downloadChecksum) {
                throw IllegalStateException("Copied udocker installation has a bad checksum. File corruption?")
            }

            startProcess(
                listOf(
                    "/usr/bin/tar",
                    "xfz",
                    udockerTarGz.absolutePath
                ),
                workingDir = installDir
            )

            return extractedDir.absolutePath
        }
    }

    suspend fun pullImage(image: String) {
        val path = install()
        startProcessAndCollectToString(
            listOf(
                File(path, "udocker").absolutePath,
                "pull",
                image
            ).also { println(it) }
        ).also {
            println(it.statusCode)
            println(it.stdout)
            println(it.stderr)
        }
    }

    private fun computeChecksum(file: File): String {
        val sha1 = MessageDigest.getInstance("SHA1")
        val buf = ByteArray(1024 * 4)
        file.inputStream().use { ins ->
            while (true) {
                val read = ins.read(buf)
                if (read == -1) break
                sha1.update(buf, 0, read)
            }
        }

        return sha1.digest().encodeBase64()
    }

    companion object {
        // NOTE(Dan): Remember to update the checksum below
        private const val downloadUrl = "https://github.com/indigo-dc/udocker/releases/download/v1.3.4/udocker-1.3.4.tar.gz"

        // NOTE(Dan): Compute with `sha1sum udocker.tar.gz | cut -f1 -d\  | xxd -r -p | base64`
        private const val downloadChecksum = "G45SBkdAwEub+dlKQXECoRqzNWQ="
    }
}

private object UDockerIpc : IpcContainer("udocker") {
    val install = updateHandler("install", Unit.serializer(), FindByPath.serializer())
}

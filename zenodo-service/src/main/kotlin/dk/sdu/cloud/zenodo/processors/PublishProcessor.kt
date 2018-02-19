package dk.sdu.cloud.zenodo.processors

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.RequestOneTimeToken
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.stream
import dk.sdu.cloud.storage.api.DOWNLOAD_FILE_SCOPE
import dk.sdu.cloud.storage.api.DownloadByURI
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.zenodo.api.ZenodoCommandStreams
import dk.sdu.cloud.zenodo.services.ZenodoResponse
import dk.sdu.cloud.zenodo.services.ZenodoService
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.StreamsBuilder
import org.asynchttpclient.request.body.multipart.FilePart
import org.slf4j.LoggerFactory
import java.nio.file.Files

class PublishProcessor(
    private val zenodoService: ZenodoService,
    private val cloudContext: CloudContext
) {
    fun init(kBuilder: StreamsBuilder) {
        kBuilder.stream(ZenodoCommandStreams.publishCommands).foreach { _, command ->
            runBlocking {
                // TODO When we change the design of commands are handled we still likely need two streams.
                // The first in which we need to authenticate the request, and the second in which we handle it.
                // This will, however, require more support for services acting on behalf of pre-authenticated users.
                // Because we don't have support for this yet, we will simply handle the request immediately.

                val validatedPrincipal =
                    TokenValidation.validateOrNull(command.header.performedFor)
                            ?: return@runBlocking run {
                                log.info("Token for command is not valid or is missing the correct scope!")
                                log.debug("Bad command was: ${command.header}")
                            }

                // TODO We need to add caused by here!
                val cloud = JWTAuthenticatedCloud(cloudContext, validatedPrincipal.token)

                val buffer = ByteArray(4096)
                val files = command.event.filePaths.map {
                    /*
                    val tokenResponse = AuthDescriptions.requestOneTimeTokenWithAudience.call(
                        RequestOneTimeToken(DOWNLOAD_FILE_SCOPE), cloud
                    )

                    if (tokenResponse !is RESTResponse.Ok) {
                        log.warn("Unable to request a new one time token for download!")
                        log.warn("${tokenResponse.status}: ${tokenResponse.rawResponseBody}")
                        return@runBlocking
                    }

                    val token = tokenResponse.result

                    // This will generate misleading audit trail, unless we get a caused-by header added to it
                    val fileDownload = FileDescriptions.download.call(DownloadByURI(it, token.accessToken), cloud)
                    val tempFile = Files.createTempFile("zenodo-upload", "").toFile()
                    try {
                        tempFile.outputStream().use { out ->
                            fileDownload.response.responseBodyAsStream.use { inp ->
                                while (true) {
                                    val read = inp.read(buffer)
                                    if (read != -1) out.write(buffer, 0, read)
                                    else break
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        log.warn("Caught exception while download files temporarily!")
                        log.warn(ex.stackTraceToString())
                        return@runBlocking
                    }
                    */

                    val tempFile = Files.createTempFile("zenodo-upload", "").toFile()
                    tempFile.writeText("Hello, World!")
                    tempFile
                }

                log.debug("Creating deposition at Zenodo!")
                val deposition = zenodoService.createDeposition(validatedPrincipal)
                log.debug("Response was: $deposition")

                if (deposition !is ZenodoResponse.Success) {
                    log.warn("Unable to create a response at Zenodo!")
                    log.warn("Response was: $deposition")
                    return@runBlocking
                }

                val depositionId = deposition.result.id
                files.forEachIndexed { idx, file ->
                    val path = command.event.filePaths[idx]
                    val name = path.substringAfterLast("/")
                    log.debug("Uploading file: $name ${file.length()}")
                    val uploadResponse = zenodoService.createUpload(
                        validatedPrincipal,
                        depositionId,
                        file
                    )

                    if (uploadResponse !is ZenodoResponse.Success) {
                        log.warn("Upload failed for file: $name")
                        log.warn("Response was: $uploadResponse")
                        return@runBlocking
                    }
                }

                // TODO This won't always happen
                files.forEach { it.delete() }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishProcessor::class.java)
    }
}
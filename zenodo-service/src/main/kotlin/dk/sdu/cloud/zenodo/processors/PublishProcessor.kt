package dk.sdu.cloud.zenodo.processors

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.RequestOneTimeToken
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.stream
import dk.sdu.cloud.storage.api.DownloadByURI
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.zenodo.api.ZenodoCommandStreams
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.StreamsBuilder
import java.nio.file.Files

class PublishProcessor(val cloudContext: CloudContext) {
    fun init(kStreams: StreamsBuilder) {
        kStreams.stream(ZenodoCommandStreams.publishCommands).foreach { _, command ->
            runBlocking {
                // TODO When we change the design of commands are handled we still likely need two streams.
                // The first in which we need to authenticate the request, and the second in which we handle it.
                // This will, however, require more support for services acting on behalf of pre-authenticated users.
                // Because we don't have support for this yet, we will simply handle the request immediately.

                val validatedPrincipal =
                    TokenValidation.validateOrNull(command.header.performedFor, listOf("api")) ?: return@runBlocking

                // TODO We need to add caused by here!
                val cloud = JWTAuthenticatedCloud(cloudContext, validatedPrincipal.token)
                val token = (AuthDescriptions.requestOneTimeTokenWithAudience.call(
                    RequestOneTimeToken("downloadFile"), cloud
                ) as? RESTResponse.Ok)?.result ?: return@runBlocking

                val jobs = command.event.filePaths.map {
                    // TODO We need to rewrite the client to avoid keeping this stuff in memory? I am not sure what
                    // will happen here. It might work
                    async { FileDescriptions.download.call(DownloadByURI(it, token.accessToken), cloud) }
                }

                jobs.forEach { it.join() }
                jobs.map {
                    val result = it.await()
                    val file = Files.createTempFile("file", "")
                    // TODO implement this
                    result.response.responseBodyAsStream
                    file.toFile().writer()
                }
            }
        }
    }
}
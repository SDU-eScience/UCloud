package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StatRequest

suspend fun waitForFile(path: String, client: AuthenticatedClient) {
    retrySection {
        FileDescriptions.stat.call(
            StatRequest(path),
            client
        ).orThrow()
    }
}

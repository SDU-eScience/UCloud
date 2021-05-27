package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.integration.retrySection

suspend fun waitForFile(path: String, client: AuthenticatedClient) {
    /*
    retrySection {
        FileDescriptions.stat.call(
            StatRequest(path),
            client
        ).orThrow()
    }
     */
}

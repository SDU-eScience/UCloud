package dk.sdu.cloud.activity.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile

private const val ONE_MINUTE = 1000L * 60 * 1

class FileLookupService(
    private val cloud: AuthenticatedClient
) {
    suspend fun lookupFile(
        path: String,
        userAccessToken: String,
        causedBy: String?
    ): StorageFile {
        val serviceCloud = cloud//.optionallyCausedBy(causedBy)

        val userCloudExtension = AuthDescriptions.tokenExtension.call(
            TokenExtensionRequest(
                userAccessToken,
                listOf(
                    SecurityScope.construct(listOf(FileDescriptions.namespace), AccessRight.READ).toString()
                ),
                expiresIn = ONE_MINUTE
            ),
            serviceCloud
        ).orThrow()

        val userCloud = cloud.withoutAuthentication().bearerAuth(userCloudExtension.accessToken)
        return FileDescriptions.stat.call(StatRequest(path), userCloud).orThrow()
    }
}

package dk.sdu.cloud.activity.services

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.StorageFile

private const val ONE_MINUTE = 1000L * 60 * 1

class FileLookupService(
    private val cloud: AuthenticatedCloud
) {
    private val cloudContext: CloudContext = cloud.parent

    suspend fun lookupFile(
        path: String,
        userAccessToken: String,
        causedBy: String?
    ): StorageFile {
        val serviceCloud = cloud.optionallyCausedBy(causedBy)

        val userCloud = AuthDescriptions.tokenExtension.call(
            TokenExtensionRequest(
                userAccessToken,
                listOf(FileDescriptions.stat.requiredAuthScope.toString()),
                expiresIn = ONE_MINUTE
            ),
            serviceCloud
        ).orThrow().asCloud(cloudContext, causedBy)

        return FileDescriptions.stat.call(FindByPath(path), userCloud).orThrow()
    }
}

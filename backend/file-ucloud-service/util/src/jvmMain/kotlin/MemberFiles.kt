package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.ucloud.services.PathConverter.Companion.PERSONAL_REPOSITORY

class MemberFiles(
    private val fs: NativeFS,
    private val paths: PathConverter,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun initializeMemberFiles(username: String, project: String?) {
        if (username.contains("..") || username.contains("/")) {
            throw IllegalArgumentException("Unexpected username: $username")
        }

        if (project != null) {
            if (project.contains("..") || project.contains("/")) {
                throw IllegalArgumentException("Unexpected project: $project")
            }

            val file = paths.relativeToInternal(
                RelativeInternalFile("/projects/${project}/${PERSONAL_REPOSITORY}/${username}")
            )

            val exists = try {
                fs.stat(file)
                true
            } catch (ex: FSException.NotFound) {
                false
            }

            if (exists) return

            try {
                FileCollectionsControl.register.call(
                    bulkRequestOf(
                        ProviderRegisteredResource(
                            FileCollection.Spec("Member Files: ${username}", paths.projectHomeProductReference),
                            "${PathConverter.COLLECTION_PROJECT_MEMBER_PREFIX}${project}/${username}",
                            createdBy = username,
                            project = project
                        )
                    ),
                    serviceClient
                ).orThrow()
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.Conflict) {
                    // Allow and create the folder
                } else {
                    throw ex
                }
            }

            fs.createDirectories(file)
        } else {
            val file = paths.relativeToInternal(
                RelativeInternalFile("/${PathConverter.HOME_DIRECTORY}/${username}")
            )

            val exists = try {
                fs.stat(file)
                true
            } catch (ex: FSException.NotFound) {
                false
            }

            if (exists) return

            try {
                fs.createDirectories(
                    paths.relativeToInternal(
                        RelativeInternalFile("/${PathConverter.HOME_DIRECTORY}/${username}/Jobs")
                    )
                )

                fs.createDirectories(
                    paths.relativeToInternal(
                        RelativeInternalFile("/${PathConverter.HOME_DIRECTORY}/${username}/Trash")
                    )
                )

                FileCollectionsControl.register.call(
                    bulkRequestOf(
                        ProviderRegisteredResource(
                            FileCollection.Spec("Home", paths.productReference),
                            "${PathConverter.COLLECTION_HOME_PREFIX}${username}",
                            createdBy = username
                        )
                    ),
                    serviceClient
                ).orThrow()
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.Conflict) {
                    // Allow and create the folder
                } else {
                    throw ex
                }
            }

            fs.createDirectories(file)
        }
    }
}

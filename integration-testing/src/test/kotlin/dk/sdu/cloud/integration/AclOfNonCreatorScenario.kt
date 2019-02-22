package dk.sdu.cloud.integration

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.CreateDirectoryRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.test.retrySection
import dk.sdu.cloud.share.api.AcceptShareRequest
import dk.sdu.cloud.share.api.CreateShareRequest
import dk.sdu.cloud.share.api.ShareDescriptions
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.Test

class AclOfNonCreatorScenario {
    @Test
    fun runScenario() = runBlocking {
        log.info("Running scenario...")
        val users = (1..3).map { "test-${UUID.randomUUID()}_${it}_" }
        val password = UUID.randomUUID().toString()

        val clouds = UserDescriptions.createNewUser.call(
            users.map { CreateSingleUserRequest(it, password, Role.USER) },
            adminClient
        ).orThrow().map { it.client() }

        val homeDir = "/home/${users[0]}"
        val filePath = "$homeDir/foo"
        val nestedPath = "$filePath/bar"

        log.info("Sharing home directory with user[1]")
        retrySection(attempts = 10) {
            val share = ShareDescriptions.create.call(
                CreateShareRequest(users[1], homeDir, AccessRight.values().toSet()),
                clouds[0]
            ).orThrow().id

            ShareDescriptions.accept.call(
                AcceptShareRequest(share, createLink = null),
                clouds[1]
            ).orThrow()
        }

        log.info("Creating directory $filePath as users[1]")
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(filePath, owner = null),
            clouds[1]
        ).orThrow()

        log.info("Sharing new directory to users[2]")
        val reshare = ShareDescriptions.create.call(
            CreateShareRequest(users[2], filePath, AccessRight.values().toSet()),
            clouds[0]
        ).orThrow().id

        ShareDescriptions.accept.call(
            AcceptShareRequest(reshare, createLink = null),
            clouds[2]
        ).orThrow()

        log.info("Creating directory in re-shared dir")
        FileDescriptions.createDirectory.call(
            CreateDirectoryRequest(nestedPath, null),
            clouds[2]
        ).orThrow()

        return@runBlocking
    }

    companion object : Loggable {
        override val log = logger()
    }
}

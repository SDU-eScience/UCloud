package dk.sdu.cloud.file.http

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.CreateLinkRequest
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.acl
import dk.sdu.cloud.file.services.acl.AccessRights
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.file.util.touch
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.withKtorTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class SymlinkTests {
    private val userFile = "user-file"

    @Test
    fun `test valid symlink between two home directories`() {
        withKtorTest(
            setup = {
                configureServerWithFileController(
                    fsRootInitializer = { createFs() },
                    fileUpdateAclWhitelist = setOf(TestUsers.user.username)
                )
            },
            test = {
                updateAcl(
                    UpdateAclRequest(
                        pathTo(TestUsers.user, userFile),
                        listOf(ACLEntryRequest(TestUsers.user2.username, AccessRights.READ_ONLY))
                    ),
                    TestUsers.user
                ).assertSuccess()

                Thread.sleep(500) // Not ideal. We should instead wait for the UpdateAclRequest to finish.

                createLink(
                    CreateLinkRequest(
                        pathTo(TestUsers.user2, "u2-link"),
                        pathTo(TestUsers.user, userFile)
                    ),
                    TestUsers.user2
                ).assertSuccess()

                val userFile = stat(
                    StatRequest(pathTo(TestUsers.user, userFile)),
                    TestUsers.user
                ).parseSuccessful<StorageFile>()

                assertThatInstance(
                    userFile,
                    "has correct acl",
                    matcher = { file ->
                        file.acl!!.any { it.entity == TestUsers.user2.username && it.rights == AccessRights.READ_ONLY }
                    }
                )

                val user2File = stat(
                    StatRequest(pathTo(TestUsers.user2, "u2-link")),
                    TestUsers.user2
                ).parseSuccessful<StorageFile>()

                assertThatInstance(
                    user2File,
                    "has correct acl",
                    matcher = { file ->
                        file.acl!!.any { it.entity == TestUsers.user2.username && it.rights == AccessRights.READ_ONLY }
                    }
                )
            }
        )
    }

    private fun createFs(): File {
        return Files.createTempDirectory("share-service-test").toFile().apply {
            mkdir("home") {
                mkdir(TestUsers.user.username) {
                    touch(userFile)
                }

                mkdir(TestUsers.user2.username) {
                    touch("user2-file")
                }
            }
        }
    }

    private fun pathTo(user: SecurityPrincipal, path: String): String = "/home/${user.username}/$path"
}

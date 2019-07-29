package dk.sdu.cloud.file.http

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.CreateLinkRequest
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.ListDirectoryRequest
import dk.sdu.cloud.file.api.LookupFileInDirectoryRequest
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.api.acl
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.acl.AccessRights
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.file.util.touch
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.KtorApplicationTestContext
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
    private val user2Link = "u2-link"

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
                createSymlink()

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
                    StatRequest(pathTo(TestUsers.user2, user2Link)),
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

    @Test
    fun `test file lookup to symlink`() {
        withKtorTest(
            setup = {
                configureServerWithFileController(
                    fsRootInitializer = { createFs() },
                    fileUpdateAclWhitelist = setOf(TestUsers.user.username)
                )
            },

            test = {
                createSymlink()

                val userPage = lookup(
                    LookupFileInDirectoryRequest(
                        pathTo(TestUsers.user, userFile),
                        50,
                        SortOrder.DESCENDING,
                        FileSortBy.PATH
                    ),
                    TestUsers.user
                ).parseSuccessful<Page<StorageFile>>()

                assertThatInstance(
                    userPage.items.find { it.path.contains(userFile) }!!,
                    "has correct acl",
                    matcher = { file ->
                        file.acl!!.any { it.entity == TestUsers.user2.username && it.rights == AccessRights.READ_ONLY }
                    }
                )

                println(userPage)

                val user2Page = lookup(
                    LookupFileInDirectoryRequest(
                        pathTo(TestUsers.user2, user2Link),
                        50,
                        SortOrder.DESCENDING,
                        FileSortBy.PATH
                    ),
                    TestUsers.user2
                ).parseSuccessful<Page<StorageFile>>()

                assertThatInstance(
                    user2Page.items.find { it.path.contains(user2Link) }!!,
                    "has correct acl",
                    matcher = { file ->
                        file.acl!!.any { it.entity == TestUsers.user2.username && it.rights == AccessRights.READ_ONLY }
                    }
                )

                println(user2Page)
            }
        )
    }

    @Test
    fun `test file lookup through symlink`() {
        val sharedFile = "a"
        withKtorTest(
            setup = {
                configureServerWithFileController(
                    fsRootInitializer = {
                        Files.createTempDirectory("test").toFile().apply {
                            mkdir("home") {
                                mkdir(TestUsers.user.username) {
                                    mkdir(userFile) {
                                        touch(sharedFile)
                                    }
                                }

                                mkdir(TestUsers.user2.username) {

                                }
                            }
                        }
                    },
                    fileUpdateAclWhitelist = setOf(TestUsers.user.username)
                )
            },

            test = {
                createSymlink()

                lookup(
                    LookupFileInDirectoryRequest(
                        pathTo(TestUsers.user, userFile),
                        50,
                        SortOrder.DESCENDING,
                        FileSortBy.PATH
                    ),
                    TestUsers.user
                ).assertSuccess()

                lookup(
                    LookupFileInDirectoryRequest(
                        pathTo(TestUsers.user2, "$user2Link/$sharedFile"),
                        50,
                        SortOrder.DESCENDING,
                        FileSortBy.PATH
                    ),
                    TestUsers.user2
                ).assertSuccess()
            }
        )
    }

    @Test
    fun `test listing directory with bad symlink`() {
        withKtorTest(
            setup = {
                configureServerWithFileController(
                    fsRootInitializer = { createFs() },
                    fileUpdateAclWhitelist = setOf(TestUsers.user.username)
                )
            },

            test = {
                createSymlink()

                updateAcl(
                    UpdateAclRequest(
                        pathTo(TestUsers.user, userFile),
                        listOf(ACLEntryRequest(TestUsers.user2.username, emptySet(), revoke = true))
                    ),
                    TestUsers.user
                )

                Thread.sleep(500)

                repeat(50) { println() }

                val directoryListing = listDirectory(
                    ListDirectoryRequest(
                        pathTo(TestUsers.user2, ""),
                        null,
                        null,
                        null,
                        null
                    ),
                    TestUsers.user2
                ).parseSuccessful<Page<StorageFile>>()

                println(directoryListing)
            }
        )
    }

    private fun KtorApplicationTestContext.createSymlink() {
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
                pathTo(TestUsers.user2, user2Link),
                pathTo(TestUsers.user, userFile)
            ),
            TestUsers.user2
        ).assertSuccess()
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

package dk.sdu.cloud.storage.http.share

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.definition
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.api.FindByShareId
import dk.sdu.cloud.storage.api.SharesByPath
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.http.ShareController
import dk.sdu.cloud.storage.http.files.setUser
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use
import org.junit.Test
import kotlin.test.assertEquals

class ShareTests {
    // Possible problem when tests are run on other computer. Schulz is not the owner of the filesystem.
    @Test
    fun createListAndAcceptTest() {
        objectMockk(NotificationDescriptions).use {
            withAuthMock {
                val userToRunAs = "user"
                val userToShareWith = "user1"

                withTestApplication(
                    moduleFunction = {
                        val instance = ServiceInstance(
                            dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                            "localhost",
                            42000
                        )
                        installDefaultFeatures(
                            mockk(relaxed = true),
                            mockk(relaxed = true),
                            instance,
                            requireJobId = false
                        )
                        install(JWTProtection)

                        val fsRoot = createDummyFS()
                        val fs = cephFSWithRelaxedMocks(
                            fsRoot.absolutePath,
                            cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer(userToRunAs)
                        )

                        val ss = ShareService(InMemoryShareDAO(), fs)
                        coEvery { NotificationDescriptions.create.call(any(), any()) } answers {
                            RESTResponse.Ok(mockk(relaxed = true), mockk(relaxed = true))
                        }

                        routing {
                            route("api") {
                                ShareController(ss, fs).configure(this)
                                FilesController(fs).configure(this)
                            }
                        }
                    },

                    test = {

                        val response = handleRequest(HttpMethod.Put, "/api/shares") {
                            setUser(userToRunAs, Role.USER)
                            setBody(
                                """
                            {
                            "sharedWith" : "$userToShareWith",
                            "path" : "/home/$userToShareWith/folder/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                            )
                        }.response

                        assertEquals(HttpStatusCode.OK, response.status())
                        val mapper = jacksonObjectMapper()
                        val id = response.content!!.let { mapper.readValue<FindByShareId>(it).id }

                        val response1 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                            setUser(userToRunAs, Role.USER)
                        }.response

                        assertEquals(HttpStatusCode.OK, response1.status())

                        val response2 = handleRequest(HttpMethod.Post, "/api/shares/accept/$id") {
                            setUser(userToShareWith, Role.USER)
                        }.response

                        assertEquals(HttpStatusCode.OK, response2.status())

                        val response3 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                            setUser(userToShareWith, Role.USER)
                        }.response

                        assertEquals(HttpStatusCode.OK, response3.status())
                        val obj = mapper.readValue<Page<SharesByPath>>(response3.content!!)
                        val share = obj.items.first().shares.find { it.id == id }
                        assertEquals(share?.state.toString(), "ACCEPTED")
                    }
                )
            }
        }
    }

    @Test
    fun createNotOwnerOfFilesTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer("user")
                    )
                    val ss = ShareService(InMemoryShareDAO(), fs)

                    routing {
                        route("api") {
                            ShareController(ss, fs).configure(this)
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {

                    val response = handleRequest(HttpMethod.Put, "/api/shares") {
                        setUser("user1", Role.USER)
                        setBody(
                            """
                            {
                            "sharedWith" : "user2",
                            "path" : "/home/user1/folder/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    assertEquals(HttpStatusCode.Forbidden, response.status())


                }
            )
        }
    }

    @Test
    fun createNonexistingPathTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer("user")
                    )
                    val ss = ShareService(InMemoryShareDAO(), fs)

                    routing {
                        route("api") {
                            ShareController(ss, fs).configure(this)
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {

                    val response = handleRequest(HttpMethod.Put, "/api/shares") {
                        setUser("user", Role.USER)
                        setBody(
                            """
                            {
                            "sharedWith" : "user1",
                            "path" : "/home/user1/folder/notThere/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            )
        }
    }


    @Test
    fun createListAndRejectTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer("user")
                    )
                    val ss = ShareService(InMemoryShareDAO(), fs)

                    routing {
                        route("api") {
                            ShareController(ss, fs).configure(this)
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {

                    val response = handleRequest(HttpMethod.Put, "/api/shares") {
                        setUser("user", Role.USER)
                        setBody(
                            """
                            {
                            "sharedWith" : "user1",
                            "path" : "/home/user1/folder/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    val mapper = jacksonObjectMapper()
                    val id = response.content!!.let { mapper.readValue<FindByShareId>(it).id }

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())
                    val obj = mapper.readTree(response1.content)
                    assertEquals("1", obj["itemsInTotal"].toString())

                    val response2 = handleRequest(HttpMethod.Post, "/api/shares/reject/$id") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user1", Role.USER)
                    }.response
                    println(response3.content)

                    assertEquals(HttpStatusCode.OK, response3.status())
                    val obj1 = mapper.readTree(response3.content)
                    assertEquals("0", obj1["itemsInTotal"].toString())

                }
            )
        }
    }

    @Test
    fun createListAndRevokeTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer("user")
                    )
                    val ss = ShareService(InMemoryShareDAO(), fs)

                    routing {
                        route("api") {
                            ShareController(ss, fs).configure(this)
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {

                    val response = handleRequest(HttpMethod.Put, "/api/shares") {
                        setUser("user", Role.USER)
                        setBody(
                            """
                            {
                            "sharedWith" : "user1",
                            "path" : "/home/user1/folder/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    val mapper = jacksonObjectMapper()
                    val id = response.content!!.let { mapper.readValue<FindByShareId>(it).id }

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())
                    val obj = mapper.readTree(response1.content)
                    assertEquals("1", obj["itemsInTotal"].toString())

                    val response2 = handleRequest(HttpMethod.Post, "/api/shares/revoke/$id") {
                        setUser("user", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user1", Role.USER)
                    }.response

                    println(response3.content)
                    assertEquals(HttpStatusCode.OK, response3.status())
                    val obj1 = mapper.readTree(response3.content)
                    assertEquals("0", obj1["itemsInTotal"].toString())
                }
            )
        }
    }

    @Test
    fun createListAndUpdateTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer("user")
                    )
                    val ss = ShareService(InMemoryShareDAO(), fs)

                    routing {
                        route("api") {
                            ShareController(ss, fs).configure(this)
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {

                    val response = handleRequest(HttpMethod.Put, "/api/shares") {
                        setUser("user", Role.USER)
                        setBody(
                            """
                            {
                            "sharedWith" : "user1",
                            "path" : "/home/user1/folder/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    val mapper = jacksonObjectMapper()
                    val id = response.content!!.let { mapper.readValue<FindByShareId>(it).id }

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())

                    val obj = mapper.readValue<Page<SharesByPath>>(response1.content!!)
                    val share = obj.items.first().shares.find { it.id == id }
                    assert(
                        share?.rights.toString().contains("READ") && share?.rights.toString().contains("EXECUTE") && !(share?.rights.toString().contains(
                            "WRITE"
                        ))
                    )


                    val response2 = handleRequest(HttpMethod.Post, "/api/shares/") {
                        setUser("user", Role.USER)
                        setBody(
                            """
                            {
                            "id" : "$id",
                            "rights" : ["WRITE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response3.status())
                    val obj1 = mapper.readValue<Page<SharesByPath>>(response3.content!!)
                    val share1 = obj1.items.first().shares.find { it.id == id }
                    assertEquals("[WRITE]", share1?.rights.toString())
                }
            )
        }
    }

    @Test
    fun createListAndUpdateNotOwnFileTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        cloudToCephFsDao = cloudToCephFsDAOWithFixedAnswer("user")
                    )
                    val ss = ShareService(InMemoryShareDAO(), fs)

                    routing {
                        route("api") {
                            ShareController(ss, fs).configure(this)
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {

                    val response = handleRequest(HttpMethod.Put, "/api/shares") {
                        setUser("user", Role.USER)
                        setBody(
                            """
                            {
                            "sharedWith" : "user1",
                            "path" : "/home/user1/folder/a",
                            "rights" : ["READ", "EXECUTE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    val mapper = jacksonObjectMapper()
                    val id = response.content!!.let { mapper.readValue<FindByShareId>(it).id }

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())

                    val obj = mapper.readValue<Page<SharesByPath>>(response1.content!!)
                    val share = obj.items.first().shares.find { it.id == id }
                    assert(
                        share?.rights.toString().contains("READ") && share?.rights.toString().contains("EXECUTE") && !(share?.rights.toString().contains(
                            "WRITE"
                        ))
                    )

                    val response2 = handleRequest(HttpMethod.Post, "/api/shares/") {
                        setUser("user1", Role.USER)
                        setBody(
                            """
                            {
                            "id" : "$id",
                            "rights" : ["WRITE"]
                            }
                            """.trimIndent()
                        )
                    }.response

                    assertEquals(HttpStatusCode.Forbidden, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/shares?itemsPerPage=10&page=0") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response3.status())
                    val obj1 = mapper.readValue<Page<SharesByPath>>(response3.content!!)
                    val share1 = obj1.items.first().shares.find { it.id == id }
                    assert(
                        share1?.rights.toString().contains("READ") && share1?.rights.toString().contains("EXECUTE") && !(share1?.rights.toString().contains(
                            "WRITE"
                        ))
                    )
                }
            )
        }
    }
}
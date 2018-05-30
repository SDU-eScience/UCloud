package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.services.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.services.cephfs.CopyService
import dk.sdu.cloud.storage.services.cephfs.RemoveService
import dk.sdu.cloud.storage.services.createDummyFS
import dk.sdu.cloud.storage.util.AuthMocking
import io.ktor.application.install
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
}

class FileControllerTest {

    val auth = AuthMocking()


    //Testing ls
    @Test
    fun listAtPathTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/user1/folder") {
                        setUser("jonas@hinchely.dk", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                }
            )
        }
    }

    @Test
    fun listAtPathIncorrectPath() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/notThere") {
                        setUser("jonas@hinchely.dk", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            )
        }
    }

    @Test
    fun missingAuth() {
        withTestApplication(
            moduleFunction = {
                val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                install(JWTProtection)
                val fsRoot = createDummyFS()
                val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                routing {
                    route("api") {
                        FilesController(fs).configure(this)
                    }
                }

            },

            test = {
                val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/user1") {
                    setUser("jonas@hinchely.dk", Role.USER)
                }.response

                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        )
    }

    //Testing Stat
    @Test
    fun statTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("jonas@hinchely.dk", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                }
            )
        }
    }

    @Test
    fun statTestNonexistingLocation() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/yep/folder/a") {
                        setUser("jonas@hinchely.dk", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                }
            )
        }
    }

    //Testing MarkFavorites and remove favorites
    @Test
    fun markAsFavoriteFileAndRemoveTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {

                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/Favorites/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response1 = handleRequest(HttpMethod.Post, "/api/files/favorite?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())

                    val response2 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/Favorites/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest ( HttpMethod.Delete, "/api/files/favorite?path=/home/user1/Favorites/a" ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response3.status())

                    val response4 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/Favorites/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response4.status())
                }
            )
        }
    }

    @Test
    fun markAsFavoriteFolderAndRemoveTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/Favorites/folder") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response1 = handleRequest(HttpMethod.Post, "/api/files/favorite?path=/home/user1/folder") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())

                    val response2 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/Favorites/folder") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest ( HttpMethod.Delete, "/api/files/favorite?path=/home/user1/Favorites/folder" ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response3.status())

                    val response4 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/Favorites/folder") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response4.status())
                }
            )
        }
    }

    //Testing create directory
    @Test
    fun makeDirectoryTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/directory") {
                        setUser("user1", Role.USER)
                        setBody("""
                            {
                            "path" : "/home/user1/newDir"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/newDir") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())
                }
            )
        }
    }

    //Testing delete file
    @Test
    fun deleteFileTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath,
                        removeService = RemoveService(true))

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(HttpMethod.Delete, "/api/files") {
                        setUser("user1", Role.USER)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response3.status())
                }
            )
        }
    }

    @Test
    fun deleteFolderTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath,
                        removeService = RemoveService(true))

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(HttpMethod.Delete, "/api/files") {
                        setUser("user1", Role.USER)
                        setBody("""
                            {
                            "path" : "/home/user1/folder"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response3.status())
                }
            )
        }
    }
    //Testing move file
    @Test
    fun moveFileTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(HttpMethod.Post, "/api/files/move?path=/home/user1/folder/a&newPath=/home/user1") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response3.status())

                    val response4 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response4.status())
                }
            )
        }
    }

    //Testing copy file
    @Test
    fun copyFileTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        copyService = CopyService(true)
                    )

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response1.status())

                    val response2 = handleRequest(HttpMethod.Post, "/api/files/copy?path=/home/user1/folder/a&newPath=/home/user1/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response3.status())

                    val response4 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response4.status())
                }
            )
        }
    }

    @Test
    fun copyFileToAlreadyExistingDirectoryPathTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        copyService = CopyService(true)
                    )

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response1.status())

                    val response2 = handleRequest(HttpMethod.Post, "/api/files/copy?path=/home/user1/folder/a&newPath=/home/user1") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.Conflict, response2.status())

                }
            )
        }
    }
    @Test
    fun copyFileFromNonExistingPath() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(
                        fsRoot.absolutePath,
                        copyService = CopyService(true)
                    )

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/notHere/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())


                    val response2 = handleRequest(
                        HttpMethod.Post,
                        "/api/files/copy?path=/home/user1/folder/notHere/a&newPath=/home/user1/a"
                    ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.Forbidden, response2.status())

                }
            )
        }
    }
    //Sync File Testing
    //TODO()
    @Test
    fun syncFileTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/sync") {
                        setUser("user1", Role.USER)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            )
        }
    }

    //Testing Annotation
    //TODO(Is Annotation set??)
    @Test
    fun annotateFileTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : "K",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response1.status())
                }
            )
        }
    }

    @Test
    fun annotateFileNotADMINTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.USER)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : "K",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.Unauthorized, response.status())
                }
            )
        }
    }

    @Test
    fun annotateFilePathNonExistingTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/notthere/dir",
                            "annotatedWith" : "K",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.Forbidden, response.status())
                }
            )
        }
    }

    @Test
    fun annotateFileValidAnnotationTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : "",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response
                    //SHOULD THIS BE OKAY? TODO()
                    assertEquals(HttpStatusCode.OK, response.status())

                    val response1 = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : "0",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response
                    //SHOULD BE 400 BAD REQUEST? TODO()
                    assertEquals(HttpStatusCode.InternalServerError, response1.status())

                    val response2 = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : "Hello",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response
                    //SHOULD BE 400 BAD REQUEST? TODO()
                    assertEquals(HttpStatusCode.InternalServerError, response2.status())

                    val response3 = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : ",",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response
                    //SHOULD BE 400 BAD REQUEST? TODO()
                    assertEquals(HttpStatusCode.InternalServerError, response3.status())

                    val response4 = handleRequest(HttpMethod.Post, "/api/files/annotate") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "annotatedWith" : "\n",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response
                    //SHOULD BE 400 BAD REQUEST? TODO()
                    assertEquals(HttpStatusCode.InternalServerError, response4.status())
                }
            )
        }
    }

    //Testing MarkAsOpenAccess
    @Test
    fun markAsOpenAccessTest() {
        auth.withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/open") {
                        setUser("user1", Role.ADMIN)
                        setBody("""
                            {
                            "path" : "/home/user1/folder/a",
                            "proxyUser" : "user1"
                            }
                            """.trimIndent())
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            )
        }
    }
}
package dk.sdu.cloud.file.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.service.tokenValidation
import dk.sdu.cloud.file.http.FilesController
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FavoriteService
import dk.sdu.cloud.file.services.FileAnnotationService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.StorageUserDao
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.simpleStorageUserDao
import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.mockk
import java.io.File
import java.util.*

data class FileControllerContext(
    val cloud: AuthenticatedCloud,
    val fsRoot: String,
    val runner: UnixFSCommandRunnerFactory,
    val fs: UnixFileSystem,
    val coreFs: CoreFileSystemService<UnixFSCommandRunner>,
    val annotationService: FileAnnotationService<UnixFSCommandRunner>,
    val favoriteService: FavoriteService<UnixFSCommandRunner>,
    val eventProducer: StorageEventProducer,
    val lookupService: FileLookupService<UnixFSCommandRunner>
)

object TestContext {
    lateinit var micro: Micro

    val tokenValidation: TokenValidationJWT
        get() = micro.tokenValidation as TokenValidationJWT
}

fun Application.configureServerWithFileController(
    fsRootInitializer: () -> File = { createDummyFS() },
    userDao: StorageUserDao = simpleStorageUserDao(),
    additional: Routing.(FileControllerContext) -> Unit = {}
) {
    val micro = initializeMicro()
    micro.install(HibernateFeature)
    TestContext.micro = micro

    val cloud = mockk<AuthenticatedCloud>(relaxed = true)
    installDefaultFeatures(micro)

    val fsRoot = fsRootInitializer()
    val (runner, fs) = unixFSWithRelaxedMocks(fsRoot.absolutePath, userDao)
    val eventProducer = mockk<StorageEventProducer>(relaxed = true)
    val coreFs = CoreFileSystemService(fs, eventProducer)
    val favoriteService = FavoriteService(coreFs)
    val aclService = ACLService(fs)

    val ctx = FileControllerContext(
        cloud = cloud,
        fsRoot = fsRoot.absolutePath,
        runner = runner,
        fs = fs,
        eventProducer = eventProducer,
        coreFs = coreFs,
        annotationService = FileAnnotationService(fs, eventProducer),
        favoriteService = favoriteService,
        lookupService = FileLookupService(coreFs, favoriteService)
    )

    routing {
        configureControllers(
            with(ctx) { FilesController(
                runner,
                coreFs,
                annotationService,
                favoriteService,
                lookupService,
                aclService
            ) }
        )
        additional(ctx)
    }
}

private val mapper = jacksonObjectMapper()

fun TestApplicationEngine.call(
    method: HttpMethod,
    url: String,
    params: Map<String, String> = emptyMap(),
    rawBody: String? = null,
    jsonBody: Any? = null,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    val fullUrl =
        "$url?" + params.entries.joinToString("&") { entry -> "${entry.key}=${entry.value}" }

    return handleRequest(method, fullUrl) {
        addHeader("Job-Id", UUID.randomUUID().toString())
        setUser(user, role)

        if (rawBody != null) {
            setBody(rawBody)
            assert(jsonBody == null)
        } else if (jsonBody != null) {
            setBody(mapper.writeValueAsBytes(jsonBody))
        }
    }.response
}

fun TestApplicationEngine.move(
    path: String,
    newPath: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/move",
        mapOf("path" to path, "newPath" to newPath),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.stat(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Get, "/api/files/stat",
        mapOf("path" to path),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.makeDir(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/directory",
        rawBody = """{ "path": "$path" }""",
        user = user,
        role = role
    )
}

fun TestApplicationEngine.delete(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Delete,
        "/api/files",
        rawBody = """{ "path": "$path" }""",
        user = user,
        role = role
    )
}

fun TestApplicationEngine.createFavorite(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/favorite",
        params = mapOf("path" to path),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.deleteFavorite(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Delete,
        "/api/files/favorite",
        params = mapOf("path" to path),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.listDir(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files",
        params = mapOf("path" to path),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.lookupFileInDirectory(
    path: String,
    itemsPerPage: Int,
    sortBy: FileSortBy,
    order: SortOrder,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files/lookup",
        params = mapOf(
            "path" to path,
            "itemsPerPage" to itemsPerPage.toString(),
            "sortBy" to sortBy.toString(),
            "order" to order.toString()
        ),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.sync(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/sync",
        rawBody = """{ "path": "$path" }""",
        user = user,
        role = role
    )
}

fun TestApplicationEngine.copy(
    path: String,
    newPath: String,
    conflictPolicy: WriteConflictPolicy,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/copy",
        params = mapOf("path" to path, "newPath" to newPath, "policy" to conflictPolicy.toString()),
        user = user,
        role = role
    )
}

fun TestApplicationEngine.annotate(
    path: String,
    annotation: String,
    proxyUser: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/annotate",
        rawBody = """{ "path": "$path", "annotatedWith": "$annotation", "proxyUser": "$proxyUser" }""",
        user = user,
        role = role
    )
}

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    val token = TokenValidationMock.createTokenForUser(username, role)
    addHeader(HttpHeaders.Authorization, "Bearer $token")
}


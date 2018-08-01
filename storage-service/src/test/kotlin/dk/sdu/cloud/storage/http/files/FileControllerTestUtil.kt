package dk.sdu.cloud.storage.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.definition
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunner
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import dk.sdu.cloud.storage.util.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.simpleCloudToCephFSDao
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.*
import io.mockk.mockk
import java.io.File

data class FileControllerContext(
    val cloud: AuthenticatedCloud,
    val fsRoot: String,
    val runner: CephFSCommandRunnerFactory,
    val fs: CephFileSystem,
    val coreFs: CoreFileSystemService<CephFSCommandRunner>,
    val annotationService: FileAnnotationService<CephFSCommandRunner>,
    val favoriteService: FavoriteService<CephFSCommandRunner>,
    val eventProducer: StorageEventProducer,
    val lookupService: FileLookupService<CephFSCommandRunner>
)

fun Application.configureServerWithFileController(
    fsRootInitializer: () -> File = { createDummyFS() },
    userDao: StorageUserDao = simpleCloudToCephFSDao(),
    additional: Route.(FileControllerContext) -> Unit = {}
) {
    val instance = ServiceInstance(
        StorageServiceDescription.definition(),
        "localhost",
        42000
    )

    val cloud = mockk<AuthenticatedCloud>(relaxed = true)
    installDefaultFeatures(cloud, mockk(relaxed = true), instance, requireJobId = false)
    install(JWTProtection)

    val fsRoot = fsRootInitializer()
    val (runner, fs) = cephFSWithRelaxedMocks(fsRoot.absolutePath, userDao)
    val eventProducer = mockk<StorageEventProducer>(relaxed = true)
    val coreFs = CoreFileSystemService(fs, eventProducer)
    val favoriteService = FavoriteService(coreFs)

    val fileController = FileControllerContext(
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
        route("api") {
            val routing = this
            with(fileController) {
                FilesController(runner, coreFs, annotationService, favoriteService, lookupService).configure(routing)
            }
            additional(fileController)
        }
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
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
}


package dk.sdu.cloud.file.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.homeDirectory
import dk.sdu.cloud.file.http.ActionController
import dk.sdu.cloud.file.http.CommandRunnerFactoryForCalls
import dk.sdu.cloud.file.http.FileSecurityController
import dk.sdu.cloud.file.http.LookupController
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.UIDLookupService
import dk.sdu.cloud.file.services.WSFileSessionService
import dk.sdu.cloud.file.services.background.BackgroundExecutor
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.simpleStorageUserDao
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.util.*

data class FileControllerContext(
    val client: RpcClient,
    val authenticatedClient: AuthenticatedClient,
    val fsRoot: String,
    val runner: LinuxFSRunnerFactory,
    val fs: LinuxFS,
    val coreFs: CoreFileSystemService<LinuxFSRunner>,
    val eventProducer: StorageEventProducer,
    val lookupService: FileLookupService<LinuxFSRunner>
)

object TestContext {
    lateinit var micro: Micro
}

fun KtorApplicationTestSetupContext.configureServerWithFileController(
    fsRootInitializer: () -> File = { createDummyFS() },
    userDao: UIDLookupService = simpleStorageUserDao(),
    additional: (FileControllerContext) -> List<Controller> = { emptyList() }
): List<Controller> {
    BackgroundScope.reset()

    val fsRoot = fsRootInitializer()
    val (runner, fs) = linuxFSWithRelaxedMocks(fsRoot.absolutePath, userDao)
    val eventProducer = mockk<StorageEventProducer>(relaxed = true)
    val fileSensitivityService = FileSensitivityService(fs, eventProducer)
    val coreFs = CoreFileSystemService(fs, eventProducer, fileSensitivityService, ClientMock.authenticatedClient)
    val sensitivityService = FileSensitivityService(fs, eventProducer)
    val aclService = ACLService(runner, fs, mockk(relaxed = true))
    val homeFolderService = mockk<HomeFolderService>()
    val callRunner = CommandRunnerFactoryForCalls(runner, WSFileSessionService(runner))
    coEvery { homeFolderService.findHomeFolder(any()) } coAnswers { homeDirectory(it.invocation.args.first() as String) }

    val ctx = FileControllerContext(
        client = micro.client,
        authenticatedClient = AuthenticatedClient(micro.client, OutgoingHttpCall) {},
        fsRoot = fsRoot.absolutePath,
        runner = runner,
        fs = fs,
        eventProducer = eventProducer,
        coreFs = coreFs,
        lookupService = FileLookupService(coreFs)
    )

    with(ctx) {
        micro.server.configureControllers(
            ActionController(
                callRunner,
                coreFs,
                sensitivityService,
                lookupService
            ),

            LookupController(
                callRunner,
                lookupService,
                homeFolderService
            ),

            FileSecurityController(
                callRunner,
                coreFs,
                aclService,
                sensitivityService
            )
        )
    }

    return additional(ctx)
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
        addHeader("X-No-Load", "true")
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
    role: Role = Role.USER,
    attributes: Set<StorageFileAttribute>? = null,
    sortBy: FileSortBy? = null
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files",
        params = run {
            val attribMap = if (attributes != null) {
                mapOf("attributes" to attributes.joinToString(",") { it.name })
            } else {
                emptyMap()
            }

            val sortByMap = if (sortBy != null) {
                mapOf("sortBy" to sortBy.name)
            } else {
                emptyMap()
            }

            mapOf("path" to path) + attribMap + sortByMap
        },
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

fun TestApplicationEngine.extract(
    path: String,
    user: String = "user1",
    role: Role = Role.USER
): TestApplicationResponse {
    return call(
        HttpMethod.Post,
        "/api/files/extract",
        rawBody = """{ "path" : "$path"}""",
        user = user,
        role = role
    )
}

fun TestApplicationEngine.findHome(
    username: String,
    user: String = "user1",
    role: Role = Role.ADMIN
): TestApplicationResponse {
    return call(
        HttpMethod.Get,
        "/api/files/homeFolder",
        params = mapOf(
            "username" to username
        ),
        user = user,
        role = role
    )
}


fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    val token = TokenValidationMock.createTokenForUser(username, role)
    addHeader(HttpHeaders.Authorization, "Bearer $token")
}


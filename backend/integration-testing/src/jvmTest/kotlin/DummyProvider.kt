package dk.sdu.cloud.integration

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.app.orchestrator.api.IngressProvider
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.app.orchestrator.api.LicenseProvider
import dk.sdu.cloud.app.orchestrator.api.NetworkIPProvider
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.Service
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

object DummyProviderService : Service {
    override val description: ServiceDescription = object : ServiceDescription {
        override val name = "dummy"
        override val version = "1"
    }

    override fun initializeServer(micro: Micro): CommonServer = DummyProvider(micro)
}

@Serializable
data class DummyConfiguration(
    val providerRefreshToken: String,
    val ucloudCertificate: String,
)

const val DUMMY_PROVIDER = "dummy"
interface DummyApi {
    val tracker: RequestTracker
    val products: List<Product>
}

object DummyJobs : JobsProvider(DUMMY_PROVIDER), DummyApi {
    override val tracker = RequestTracker()
    override val products: List<Product> = listOf()
}

object DummyIngress : IngressProvider(DUMMY_PROVIDER), DummyApi {
    override val tracker = RequestTracker()
    val perUnitIngress = Product.Ingress(
        "ingress",
        1L,
        ProductCategoryId("ingress", DUMMY_PROVIDER),
        "Ingress"
    )

    override val products: List<Product> = listOf(perUnitIngress)
}

object DummyLicense : LicenseProvider(DUMMY_PROVIDER), DummyApi {
    override val tracker = RequestTracker()
    override val products: List<Product> = listOf()
}

object DummyIps : NetworkIPProvider(DUMMY_PROVIDER), DummyApi {
    override val tracker = RequestTracker()
    override val products: List<Product> = listOf()
}

object DummyFileCollections : FileCollectionsProvider(DUMMY_PROVIDER), DummyApi {
    override val tracker = RequestTracker()
    override val products: List<Product> = listOf()
}

object DummyFiles : FilesProvider(DUMMY_PROVIDER), DummyApi {
    override val tracker = RequestTracker()
    override val products: List<Product> = listOf()
}

class DummyProvider(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val configuration = micro.configuration.requestChunkAt<DummyConfiguration>("dummy")

        val (refreshToken, validation) = Pair(
            configuration.providerRefreshToken,
            InternalTokenValidationJWT.withPublicCertificate(configuration.ucloudCertificate)
        )
        val authenticator = RefreshingJWTAuthenticator(micro.client, JwtRefresher.Provider(refreshToken))
        val serviceClient = authenticator.authenticateClient(OutgoingHttpCall)

        configureControllers(
            dummyController(
                serviceClient,
                DummyIngress,
                DummyIngress.tracker,
                listOf(),
                configure = {}
            ),
            dummyController(
                serviceClient,
                DummyLicense,
                DummyLicense.tracker,
                listOf(),
                configure = {}
            ),
            dummyController(
                serviceClient,
                DummyIps,
                DummyIps.tracker,
                listOf(),
                configure = {}
            ),
            dummyController(
                serviceClient,
                DummyFileCollections,
                DummyFileCollections.tracker,
                listOf(),
                configure = {}
            ),
            dummyController(
                serviceClient,
                DummyFiles,
                DummyFiles.tracker,
                listOf(),
                configure = {}
            ),
        )
    }
}

data class TrackedRequest<R : Any>(val call: CallDescription<R, *, *>, val request: R)
class RequestTracker {
    private val mutex = Mutex()
    private val _trackedRequests = ArrayList<TrackedRequest<*>>()
    val trackedRequests: List<TrackedRequest<*>> get() = _trackedRequests

    suspend fun <R : Any> track(call: CallDescription<R, *, *>, request: R) {
        mutex.withLock {
            _trackedRequests.add(TrackedRequest(call, request))
        }
    }
}

fun <Prod : Product, Support : ProductSupport> dummyController(
    client: AuthenticatedClient,
    api: ResourceProviderApi<*, *, *, *, *, Prod, Support>,
    tracker: RequestTracker,
    retrieveProducts: List<ResolvedSupport<Prod, Support>>,
    configure: RpcServer.() -> Unit
): DummyController<*, *, *, *, *, *, *> {
    return object : DummyController<Nothing, Nothing, Nothing, Nothing, Nothing, Nothing, Nothing>(
        client,
        api as ResourceProviderApi<Nothing, Nothing, Nothing, Nothing, Nothing, Nothing, Nothing>,
        tracker
    ) {
        override fun retrieveProducts(): List<ResolvedSupport<Nothing, Nothing>> {
            return retrieveProducts as List<ResolvedSupport<Nothing, Nothing>>
        }

        override fun configure(rpcServer: RpcServer) {
            with(rpcServer) {
                configureBaseEndpoints()
                configure()
            }
        }
    }
}

abstract class DummyController<
    Res : Resource<Prod, Support>,
    Spec : ResourceSpecification,
    Update : ResourceUpdate,
    Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>,
    Prod : Product,
    Support : ProductSupport>(
    private val client: AuthenticatedClient,
    private val api: ResourceProviderApi<Res, Spec, Update, Flags, Status, Prod, Support>,
    private val tracker: RequestTracker,
) : Controller {
    private var cachedSupport: BulkResponse<Support>? = null

    suspend fun initialize() {
        val products = retrieveProducts()
        cachedSupport = BulkResponse(products.map { it.support })
        Products.create.call(
            BulkRequest(products.map { it.product }),
            client
        ).orRethrowAs {
            throw RPCException(
                "Failed to initialize products: ${it.error} ${it.statusCode}",
                HttpStatusCode.InternalServerError
            )
        }
    }

    protected fun RpcServer.configureBaseEndpoints() {
        implement(api.create) {
            tracker.track(api.create, request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(api.updateAcl) {
            tracker.track(api.updateAcl, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(api.verify) {
            tracker.track(api.verify, request)
            ok(Unit)
        }

        implement(api.retrieveProducts) {
            tracker.track(api.retrieveProducts, request)
            ok(cachedSupport ?: error("must call initialize() first"))
        }

        api.delete?.let {
            implement(it) {
                tracker.track(it, request)
                ok(BulkResponse(request.items.map { }))
            }
        }
    }

    protected abstract fun retrieveProducts(): List<ResolvedSupport<Prod, Support>>
}

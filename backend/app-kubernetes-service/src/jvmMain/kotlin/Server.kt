package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.kubernetes.rpc.*
import dk.sdu.cloud.app.kubernetes.services.*
import dk.sdu.cloud.app.kubernetes.services.proxy.ApplicationProxyService
import dk.sdu.cloud.app.kubernetes.services.proxy.EnvoyConfigurationService
import dk.sdu.cloud.app.kubernetes.services.proxy.TunnelManager
import dk.sdu.cloud.app.kubernetes.services.proxy.VncService
import dk.sdu.cloud.app.kubernetes.services.proxy.WebService
import dk.sdu.cloud.app.orchestrator.api.IngressSupport
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.k8.KubernetesClient
import dk.sdu.cloud.service.k8.KubernetesConfigurationSource
import io.ktor.routing.routing
import kotlinx.coroutines.*
import java.io.File
import java.util.*

class Server(
    override val micro: Micro,
    private val configuration: Configuration,
    private val cephConfig: CephConfiguration,
) : CommonServer {
    override val log = logger()
    private lateinit var tunnelManager: TunnelManager
    private lateinit var vncService: VncService
    private lateinit var webService: WebService
    private lateinit var k8Dependencies: K8Dependencies

    override fun start() {
        if (!configuration.enabled) return
        val (refreshToken, validation) =
            if (configuration.providerRefreshToken == null || configuration.ucloudCertificate == null) {
                if (!micro.developmentModeEnabled) {
                    throw IllegalStateException("Missing configuration at app.kubernetes.providerRefreshToken")
                }
                Pair("REPLACED_LATER", InternalTokenValidationJWT.withSharedSecret(UUID.randomUUID().toString()))
            } else {
                Pair(
                    configuration.providerRefreshToken,
                    InternalTokenValidationJWT.withPublicCertificate(configuration.ucloudCertificate)
                )
            }

        val authenticator = RefreshingJWTAuthenticator(micro.client, JwtRefresher.Provider(refreshToken))
        @Suppress("UNCHECKED_CAST")
        micro.providerTokenValidation = validation as TokenValidation<Any>

        val broadcastingStream = RedisBroadcastingStream(micro.redisConnectionManager)
        val distributedLocks = DistributedLockBestEffortFactory(micro)
        val nameAllocator = NameAllocator()
        val db = AsyncDBSessionFactory(micro)

        val debug = micro.featureOrNull(DebugSystem)
        val serviceClient = authenticator.authenticateClient(OutgoingHttpCall)
        k8Dependencies = K8Dependencies(
            KubernetesClient(
                if (configuration.kubernetesConfig != null) {
                    KubernetesConfigurationSource.KubeConfigFile(configuration.kubernetesConfig, null)
                } else {
                    KubernetesConfigurationSource.Auto
                }
            ),

            micro.backgroundScope,
            serviceClient,
            nameAllocator,
            DockerImageSizeQuery(),
            debug,
        )

        val jobCache = VerifiedJobCache(k8Dependencies)
        val logService = K8LogService(k8Dependencies)
        val maintenance = MaintenanceService(db, k8Dependencies)
        val utilizationService = UtilizationService(k8Dependencies)
        val resourceCache = ResourceCache(k8Dependencies)
        val sessions = SessionDao()
        val ingressService = IngressService(
            IngressSupport(
                configuration.prefix,
                "." + configuration.domain,
                ProductReference("u1-publiclink", "u1-publiclink", "ucloud")
            ),
            db,
            k8Dependencies
        )
        val licenseService = LicenseService(k8Dependencies, db)
        val networkIpService = NetworkIPService(db, k8Dependencies, configuration.networkInterface ?: "")

        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")
        val pathConverter = PathConverter(InternalFile(fsRootFile.absolutePath), serviceClient)
        val fs = NativeFS(pathConverter)
        val memberFiles = MemberFiles(fs, pathConverter, serviceClient)

        val jobManagement = JobManagement(
            k8Dependencies,
            distributedLocks,
            logService,
            jobCache,
            maintenance,
            resourceCache,
            db,
            sessions,
            // NOTE(Dan): The master lock can be annoying to deal with during development (when we only have one
            // instance) In that case we can disable it via configuration. Note that this config will only be used if
            // we are in development mode.
            (configuration.disableMasterElection && micro.developmentModeEnabled),
        ).apply {
            register(TaskPlugin(
                configuration.toleration,
                configuration.useSmallReservation && micro.developmentModeEnabled,
                configuration.useMachineSelector == true,
                configuration.nodes,
            ))
            register(ParameterPlugin(licenseService, pathConverter))
            val fileMountPlugin = FileMountPlugin(
                fs,
                memberFiles,
                pathConverter,
                LimitChecker(db, pathConverter),
                cephConfig
            )
            register(fileMountPlugin)
            register(MultiNodePlugin)
            register(SharedMemoryPlugin)
            register(ExpiryPlugin)
            register(AccountingPlugin)
            register(MiscellaneousPlugin)
            register(NetworkLimitPlugin)
            register(FairSharePlugin)
            if (micro.developmentModeEnabled) register(MinikubePlugin)
            register(ingressService)
            register(networkIpService)
            register(FirewallPlugin(db, configuration.networkGatewayCidr))
            register(ProxyPlugin(broadcastingStream, ingressService))
            register(FileOutputPlugin(pathConverter, fs, logService, fileMountPlugin))

            // NOTE(Dan): Kata Containers are not currently enabled due to various limitations in Kata containers
            // related to our infrastructure setup
            // register(KataContainerPlugin())
        }

        val envoyConfigurationService = EnvoyConfigurationService(
            File("./envoy/rds.yaml"),
            File("./envoy/clusters.yaml")
        )

        run {
            val port = micro.featureOrNull(ServiceDiscoveryOverrides)?.get(micro.serviceDescription.name)?.port
                ?: 8080
            val renderedConfig =
                Server::class.java.classLoader.getResourceAsStream("config_template.yaml")
                    ?.bufferedReader()
                    ?.readText()
                    ?.replace("\$SERVICE_PORT", port.toString())
                    ?.replace("\$PWD", File("./envoy").absoluteFile.normalize().absolutePath)
                    ?: throw IllegalStateException("Could not find config_template.yml in classpath")

            val configFile = File("./envoy/config.yaml")
            configFile.writeText(renderedConfig)
            log.info("Wrote configuration at ${configFile.absolutePath}")
        }

        tunnelManager = TunnelManager(k8Dependencies)
        tunnelManager.install()

        val applicationProxyService = ApplicationProxyService(
            envoyConfigurationService,
            prefix = configuration.prefix,
            domain = configuration.domain,
            jobCache = jobCache,
            tunnelManager = tunnelManager,
            broadcastingStream = broadcastingStream,
            resources = resourceCache
        )

        vncService = VncService(db, sessions, jobCache, resourceCache, tunnelManager)
        webService = WebService(
            prefix = configuration.prefix,
            domain = configuration.domain,
            k8 = k8Dependencies,
            devMode = micro.developmentModeEnabled,
            db = db,
            sessions = sessions,
            ingressService = ingressService
        )

        configureControllers(
            AppKubernetesController(
                jobManagement,
                logService,
                webService,
                vncService,
                utilizationService
            ),
            MaintenanceController(maintenance, micro.tokenValidation),
            ShellController(k8Dependencies, db, sessions),
            IngressController(ingressService),
            LicenseController(licenseService),
            NetworkIPController(networkIpService),
        )

        runBlocking {
            applicationProxyService.initializeListener()
            jobManagement.initializeListeners()
        }

        startServices(wait = false)
    }

    override fun onKtorReady() {
        if (!configuration.enabled) return
        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!

        ktorEngine.application.routing {
            vncService.install(this)
            webService.install(this)
        }
    }

    override fun stop() {
        super.stop()
        if (!configuration.enabled) return
        tunnelManager.shutdown()
    }
}

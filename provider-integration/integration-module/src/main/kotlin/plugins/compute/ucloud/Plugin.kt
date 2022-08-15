package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.ComputeSupport
import dk.sdu.cloud.app.orchestrator.api.Ingress
import dk.sdu.cloud.app.orchestrator.api.IngressSupport
import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem
import dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem
import dk.sdu.cloud.app.orchestrator.api.JobsProviderSuspendRequestItem
import dk.sdu.cloud.app.orchestrator.api.JobsProviderUtilizationResponse
import dk.sdu.cloud.app.orchestrator.api.NetworkIP
import dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.app.orchestrator.api.ShellRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.IngressPlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.PublicIPPlugin
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.UCloudFilePlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.selects.select

class UCloudComputePlugin : ComputePlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    private lateinit var pluginConfig: ConfigSchema.Plugins.Jobs.UCloud
    private lateinit var files: UCloudFilePlugin

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    lateinit var jobCache: VerifiedJobCache
    lateinit var k8: K8Dependencies
    lateinit var jobManagement: JobManagement
    lateinit var logService: K8LogService
    lateinit var licenseService: LicenseService
    lateinit var utilization: UtilizationService
    lateinit var sessions: SessionDao
    lateinit var shell: K8Shell

    override fun configure(config: ConfigSchema.Plugins.Jobs) {
        pluginConfig = config as ConfigSchema.Plugins.Jobs.UCloud
    }

    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return

        files = config.plugins.files[pluginName] as? UCloudFilePlugin
            ?: run {
                error("Must have storage configured for the UCloud compute plugin ($pluginName). " +
                        "It appears that '${pluginName}' does not point to a valid UCloud storage plugin.")
            }

        jobCache = VerifiedJobCache(rpcClient)
        k8 = K8Dependencies(
            KubernetesClient(
                if (pluginConfig.kubeConfig != null) {
                    KubernetesConfigurationSource.KubeConfigFile(pluginConfig.kubeConfig, null)
                } else {
                    KubernetesConfigurationSource.Auto
                }
            ),
            GlobalScope,
            rpcClient,
            NameAllocator(pluginConfig.namespace),
            debugSystem,
            jobCache
        )
        sessions = SessionDao()
        jobManagement = JobManagement(
            config.core.providerId,
            k8,
            jobCache,
            MaintenanceService(dbConnection, k8),
            ResourceCache(k8),
            dbConnection,
            sessions
        )

        logService = K8LogService(k8)
        licenseService = LicenseService(config.core.providerId, k8, dbConnection)
        utilization = UtilizationService(k8)
        shell = K8Shell(sessions, k8)

        with(jobManagement) {
            register(
                FeatureTask(
                    pluginConfig.nodeToleration,
                    pluginConfig.forceMinimumReservation,
                    pluginConfig.useMachineSelector,
                    NodeConfiguration(
                        pluginConfig.systemReservedCpuMillis,
                        pluginConfig.systemReservedCpuMillis,
                        run {
                            val result = HashMap<String, NodeType>()
                            val allTypes = productAllocationResolved
                                .filterIsInstance<Product.Compute>()
                                .groupBy { it.category.name }

                            for ((category, machines) in allTypes) {
                                val cpu = machines.maxByOrNull { it.cpu ?: 1 }?.cpu ?: 1
                                val mem = machines.maxByOrNull { it.memoryInGigs ?: 1 }?.memoryInGigs ?: 1
                                val gpu = machines.maxByOrNull { it.gpu ?: 0 }?.gpu ?: 0
                                result[category] = NodeType(cpu * 1000, mem * 1024, gpu)
                            }

                            result
                        }
                    )
                )
            )

            register(FeatureParameter(licenseService, files.pathConverter))
            val fileMountPlugin = FeatureFileMount(
                files.fs,
                files.memberFiles,
                files.pathConverter,
                files.limitChecker,
            )
            register(fileMountPlugin)
            register(FeatureMultiNode)
            register(FeatureSharedMemory)
            register(FeatureExpiry)
            register(FeatureAccounting)
            register(FeatureMiscellaneous)
            register(FeatureNetworkLimit)
            register(FeatureFairShare)
//            if (micro.developmentModeEnabled) register(FeatureMinikube)
//            register(FeatureProxy(broadcastingStream, ingressService))
            register(FeatureFileOutput(files.pathConverter, files.fs, logService, fileMountPlugin))

        }
    }

    override suspend fun PluginContext.runMonitoringLoop() {
        if (!config.shouldRunServerCode()) return
        jobManagement.runMonitoring()
    }

    override suspend fun RequestContext.create(resource: Job): FindByStringId? {
        jobManagement.create(resource)
        return null
    }

    override suspend fun RequestContext.extend(request: JobsProviderExtendRequestItem) {
        jobManagement.extend(request.job, request.requestedTime)
    }

    override suspend fun RequestContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        throw RPCException("Unsupported operation", HttpStatusCode.BadRequest)
    }

    override suspend fun RequestContext.terminate(resource: Job) {
        jobManagement.cancel(resource)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {
        val channel = logService.useLogWatch(job.id)
        while (isActive() && !channel.isClosedForReceive) {
            select {
                channel.onReceiveCatching { message ->
                    val log = message.getOrNull() ?: return@onReceiveCatching Unit
                    emitStdout(log.rank, log.message)
                }

                onTimeout(500) {
                    // Do nothing, just check if we are still active
                }
            }
        }

        if (!channel.isClosedForReceive) {
            runCatching { channel.cancel() }
        }
    }

    override suspend fun RequestContext.openInteractiveSession(job: JobsProviderOpenInteractiveSessionRequestItem): OpenSession {
        return when (job.sessionType) {
            InteractiveSessionType.WEB -> TODO()
            InteractiveSessionType.VNC -> TODO()
            InteractiveSessionType.SHELL -> {
                OpenSession.Shell(
                    job.job.id,
                    job.rank,
                    jobManagement.openShellSession(listOf(JobAndRank(job.job, job.rank))).single()
                )
            }
        }
    }

    override suspend fun RequestContext.canHandleShellSession(request: ShellRequest.Initialize): Boolean {
        return sessions.findSessionOrNull(dbConnection, request.sessionIdentifier, InteractiveSessionType.SHELL) != null
    }

    override suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        with(shell) {
            handleShellSession(request)
        }
    }

    override suspend fun RequestContext.retrieveClusterUtilization(categoryId: String): JobsProviderUtilizationResponse {
        return JobsProviderUtilizationResponse(
            utilization.retrieveCapacity(categoryId),
            utilization.retrieveUsedCapacity(categoryId),
            utilization.retrieveQueueStatus(categoryId)
        )
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<ComputeSupport> {
        return BulkResponse(
            knownProducts.map {
                ComputeSupport(
                    it,
                    docker = ComputeSupport.Docker(
                        enabled = true,
                        web = jobManagement.featureOrNull<FeatureIngress>() != null,
                        vnc = jobManagement.featureOrNull<FeatureIngress>() != null,
                        logs = true,
                        terminal = true,
                        peers = true,
                        timeExtension = true,
                        utilization = true,
                    ),
                )
            }
        )
    }
}

class UCloudIngressPlugin : IngressPlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    private lateinit var compute: UCloudComputePlugin
    private lateinit var pluginConfig: ConfigSchema.Plugins.Ingresses.UCloud
    private lateinit var service: FeatureIngress

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override fun configure(config: ConfigSchema.Plugins.Ingresses) {
        pluginConfig = config as ConfigSchema.Plugins.Ingresses.UCloud
    }

    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return
        compute = (config.plugins.jobs[pluginName] as? UCloudComputePlugin)
            ?: error("UCloud ingress plugin must run with a matching compute plugin (with the same name)")

        service = FeatureIngress(pluginConfig.domainPrefix, pluginConfig.domainSuffix, dbConnection, compute.k8)

        compute.jobManagement.register(service)

        // TODO Do something with this (requires some changes to envoy)
        WebService(
            config.core.providerId,
            compute.k8,
            dbConnection,
            compute.sessions,
            pluginConfig.domainPrefix,
            pluginConfig.domainSuffix,
            service
        )
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<IngressSupport> {
        return BulkResponse(knownProducts.map {
            IngressSupport(pluginConfig.domainPrefix, pluginConfig.domainSuffix, it)
        })
    }

    override suspend fun RequestContext.create(resource: Ingress): FindByStringId? {
        service.create(bulkRequestOf(resource))
        return null
    }

    override suspend fun RequestContext.delete(resource: Ingress) {
        service.delete(bulkRequestOf(resource))
    }
}

class UCloudPublicIPPlugin : PublicIPPlugin {
    override val pluginTitle: String = "UCloud"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    private lateinit var compute: UCloudComputePlugin
    private lateinit var pluginConfig: ConfigSchema.Plugins.PublicIPs.UCloud
    private lateinit var ipMounter: FeaturePublicIP
    private lateinit var firewall: FeatureFirewall

    override fun configure(config: ConfigSchema.Plugins.PublicIPs) {
        pluginConfig = config as ConfigSchema.Plugins.PublicIPs.UCloud
    }

    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return
        compute = (config.plugins.jobs[pluginName] as? UCloudComputePlugin)
            ?: error("UCloud ingress plugin must run with a matching compute plugin (with the same name)")

        ipMounter = FeaturePublicIP(dbConnection, compute.k8, pluginConfig.iface, )
        firewall = FeatureFirewall(dbConnection, pluginConfig.gatewayCidr)

        compute.jobManagement.register(ipMounter)
        compute.jobManagement.register(firewall)
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<NetworkIPSupport> {
        return BulkResponse(knownProducts.map {
            NetworkIPSupport(it, NetworkIPSupport.Firewall(enabled = true))
        })
    }

    override suspend fun RequestContext.create(resource: NetworkIP): FindByStringId? {
        ipMounter.create(bulkRequestOf(resource))
        return null
    }

    override suspend fun RequestContext.delete(resource: NetworkIP) {
        ipMounter.delete(bulkRequestOf(resource))
    }
}

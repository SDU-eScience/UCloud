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
import dk.sdu.cloud.app.orchestrator.api.ShellRequest
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.controllers.ComputeSessionIpc
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.debug.wrongD
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.ComputeSession
import dk.sdu.cloud.plugins.IngressPlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.PublicIPPlugin
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.UCloudFilePlugin
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlin.coroutines.coroutineContext

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
    lateinit var k8: K8DependenciesImpl
    lateinit var jobManagement: JobManagement
    lateinit var logService: K8LogService
    lateinit var utilization: UtilizationService
    lateinit var shell: K8Shell
    lateinit var runtime: ContainerRuntime

    override fun configure(config: ConfigSchema.Plugins.Jobs) {
        pluginConfig = config as ConfigSchema.Plugins.Jobs.UCloud
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return

        files = config.plugins.files[pluginName] as? UCloudFilePlugin
            ?: run {
                error("Must have storage configured for the UCloud compute plugin ($pluginName). " +
                        "It appears that '${pluginName}' does not point to a valid UCloud storage plugin.")
            }

        jobCache = VerifiedJobCache(rpcClient)
        val nameAllocator = NameAllocator(pluginConfig.namespace)
        k8 = K8DependenciesImpl(
            KubernetesClient(
                if (pluginConfig.kubeSvcOverride != null) {
                    KubernetesConfigurationSource.InClusterConfiguration(pluginConfig.kubeSvcOverride)
                } else if (pluginConfig.kubeConfig != null) {
                    KubernetesConfigurationSource.KubeConfigFile(pluginConfig.kubeConfig, null)
                } else {
                    KubernetesConfigurationSource.Auto
                }
            ),
            GlobalScope,
            rpcClient,
            nameAllocator,
            debugSystem,
            jobCache
        )

        runtime = when (pluginConfig.scheduler) {
            ConfigSchema.Plugins.Jobs.UCloud.Scheduler.Volcano -> VolcanoRuntime(k8, pluginConfig.categoryToSelector,
                pluginConfig.fakeIpMount, pluginConfig.usePortForwarding)
            ConfigSchema.Plugins.Jobs.UCloud.Scheduler.Pods -> K8PodRuntime(k8.client, pluginConfig.namespace,
                pluginConfig.categoryToSelector, pluginConfig.fakeIpMount, pluginConfig.usePortForwarding)
        }

        nameAllocator.runtime = runtime

        jobManagement = JobManagement(
            pluginName,
            k8,
            runtime,
            jobCache,
            MaintenanceService(dbConnection, k8),
            ResourceCache(k8)
        )

        logService = K8LogService(k8, runtime)
        utilization = UtilizationService(k8, runtime)
        shell = K8Shell(runtime)

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

            register(FeatureParameter(this@initialize, files.pathConverter))
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
            register(FeatureFirewall)
            register(FeatureFileOutput(files.pathConverter, files.fs))
            register(FeatureSshKeys(pluginConfig.ssh?.subnets ?: emptyList()))
        }
    }

    override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        while (coroutineContext.isActive) {
            try {
                jobManagement.runMonitoring()
            } catch (ex: Throwable) {
                debugSystem.logThrowable("Caught exception while monitoring K8 jobs", ex)
            }
        }
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
                    @Suppress("RedundantUnitExpression")
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

    override suspend fun RequestContext.verify(request: BulkRequest<Job>) {
        jobManagement.verifyJobs(request.items)
    }

    override suspend fun RequestContext.openInteractiveSession(job: JobsProviderOpenInteractiveSessionRequestItem): ComputeSession {
        return when (job.sessionType) {
            InteractiveSessionType.WEB -> {
                val ingressFeature = jobManagement.featureOrNull<FeatureIngress>()
                    ?: throw RPCException("Not supported", HttpStatusCode.BadRequest)

                val publicDomain = ingressFeature.retrieveDomainsByJobId(job.job.id).firstOrNull()
                val domain = publicDomain
                    ?: ingressFeature.defaultDomainByJobIdAndRank(job.job.id, job.rank)

                val isPublic = domain == publicDomain

                val tunnel = runtime.openTunnel(
                    job.job.id,
                    job.rank,
                    job.job.status.resolvedApplication?.invocation?.web?.port ?: 80
                )

                /*
                val pod = k8.client.getResource(
                    Pod.serializer(),
                    KubernetesResourceLocator.common.pod.withNameAndNamespace(
                        k8.nameAllocator.jobIdAndRankToPodName(job.job.id, job.rank),
                        k8.nameAllocator.namespace()
                    )
                )

                val ipAddress = pod.status?.podIP ?: throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)

                 */

                ComputeSession(
                    target = ComputeSessionIpc.SessionTarget(
                        domain,
                        tunnel.hostnameOrIpAddress,
                        tunnel.port,
                        webSessionIsPublic = isPublic,
                        useDnsForAddressLookup = !tunnel.hostnameOrIpAddress[0].isDigit()
                    )
                )
            }

            InteractiveSessionType.VNC -> {
                /*
                val pod = k8.client.getResource(
                    Pod.serializer(),
                    KubernetesResourceLocator.common.pod.withNameAndNamespace(
                        k8.nameAllocator.jobIdAndRankToPodName(job.job.id, job.rank),
                        k8.nameAllocator.namespace()
                    )
                )

                val ipAddress = pod.status?.podIP ?: throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
                 */

                val tunnel = runtime.openTunnel(
                    job.job.id,
                    job.rank,
                    job.job.status.resolvedApplication?.invocation?.vnc?.port ?: 5900
                )

                ComputeSession(
                    target = ComputeSessionIpc.SessionTarget(
                        "",
                        tunnel.hostnameOrIpAddress,
                        tunnel.port,
                        useDnsForAddressLookup = !tunnel.hostnameOrIpAddress[0].isDigit()
                    )
                )
            }

            InteractiveSessionType.SHELL -> ComputeSession()
        }
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

    override fun supportsRealUserMode(): Boolean = false
    override fun supportsServiceUserMode(): Boolean = true

    override fun configure(config: ConfigSchema.Plugins.PublicIPs) {
        pluginConfig = config as ConfigSchema.Plugins.PublicIPs.UCloud
    }

    override suspend fun PluginContext.initialize() {
        if (!config.shouldRunServerCode()) return
        compute = (config.plugins.jobs[pluginName] as? UCloudComputePlugin)
            ?: error("UCloud ingress plugin must run with a matching compute plugin (with the same name)")

        ipMounter = FeaturePublicIP(dbConnection, compute.k8, pluginConfig.iface)
        firewall = compute.jobManagement.featureOrNull() ?: error("No firewall feature detected")
        pluginConfig.gatewayCidr?.let { firewall.gatewayCidr.add(it) }

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

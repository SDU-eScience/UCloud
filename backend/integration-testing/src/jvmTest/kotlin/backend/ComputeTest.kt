package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCreateFolderRequestItem
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.log
import dk.sdu.cloud.integration.UCloudLauncher.micro
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.k8.*
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ComputeTest : IntegrationTest() {
    private lateinit var figletTool: Tool
    private lateinit var figletBatch: ApplicationWithFavoriteAndTags
    private lateinit var figletLongRunning: ApplicationWithFavoriteAndTags

    private suspend fun create() {
        ToolStore.create.call(
            Unit,
            serviceClient.withHttpBody(
                """
                    ---
                    tool: v1

                    title: Figlet

                    name: figlet
                    version: 1.0.0

                    container: truek/figlets:1.1.1

                    authors:
                    - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                    description: Tool for rendering text.

                    defaultTimeAllocation:
                      hours: 0
                      minutes: 1
                      seconds: 0

                    backend: DOCKER
                """.trimIndent(),
                ContentType("text", "yaml")
            )
        ).orThrow()

        AppStore.create.call(
            Unit,
            serviceClient.withHttpBody(
                """
                   ---
                   application: v1

                   title: Figlet
                   name: figlet
                   version: 1.0.0

                   tool:
                     name: figlet
                     version: 1.0.0

                   authors:
                   - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                   description: >
                     Render some text with Figlet Docker!

                   invocation:
                   - figlet
                   - type: var
                     vars: text
                     
                   parameters:
                     text:
                       title: "Some text to render with figlet"
                       type: text
                   
                   allowAdditionalMounts: true
     
                """.trimIndent(),
                ContentType("text", "yaml")
            )
        ).orThrow()

        AppStore.create.call(
            Unit,
            serviceClient.withHttpBody(
                """
                    ---
                    application: v1
                    
                    title: long running
                    name: long-running
                    version: 1.0.0
                    
                    tool:
                      name: figlet
                      version: 1.0.0
                    
                    authors: ["Dan Sebasti2 Thrane"]
                    
                    description: Runs for a long time
                    
                    # We just count to a really big number
                    invocation:
                    - figlet-count
                    - 1000000000
                """.trimIndent(),
                ContentType("text", "yaml")
            )
        ).orThrow()

        figletTool = ToolStore.findByNameAndVersion.call(
            FindByNameAndVersion("figlet", "1.0.0"),
            serviceClient
        ).orThrow()

        figletBatch = AppStore.findByNameAndVersion.call(
            FindApplicationAndOptionalDependencies("figlet", "1.0.0"),
            serviceClient
        ).orThrow()

        figletLongRunning = AppStore.findByNameAndVersion.call(
            FindApplicationAndOptionalDependencies("long-running", "1.0.0"),
            serviceClient
        ).orThrow()
    }

    private data class StartJobParameters(
        val longRunning: Boolean,
        val interactivity: InteractiveSessionType?,
        val waitForState: JobState? = null,
        val resources: List<AppParameterValue> = emptyList(),
    )

    private suspend fun startJob(
        parameters: StartJobParameters,
        product: Product.Compute,
        rpcClient: AuthenticatedClient,
    ): Pair<String, JobState> {
        with(parameters) {
            val (meta, params) = when {
                !longRunning && interactivity == null -> {
                    Pair(figletBatch.metadata, mapOf("text" to AppParameterValue.Text("Hello, World!")))
                }

                longRunning && interactivity == null -> {
                    Pair(figletLongRunning.metadata, emptyMap())
                }

                interactivity == InteractiveSessionType.SHELL -> {
                    TODO()
                }

                interactivity == InteractiveSessionType.VNC -> {
                    TODO()
                }

                interactivity == InteractiveSessionType.WEB -> {
                    TODO()
                }

                else -> error("Should not happen: $longRunning $interactivity")
            }

            val id = Jobs.create.call(
                bulkRequestOf(
                    JobSpecification(
                        NameAndVersion(meta.name, meta.version),
                        product.toReference(),
                        timeAllocation = SimpleDuration(0, 15, 0),
                        parameters = params,
                        resources = resources
                    )
                ),
                rpcClient
            ).orThrow().responses.first()!!.id

            return Pair(id, waitForState(id, waitForState, rpcClient))
        }
    }

    private suspend fun waitForState(id: String, targetState: JobState?, rpcClient: AuthenticatedClient): JobState {
        val deadline = Time.now() + 1000 * 60 * 5L
        var lastKnownState: JobState = JobState.IN_QUEUE
        while (Time.now() < deadline && !lastKnownState.isFinal()) {
            if (targetState == lastKnownState) break
            lastKnownState = Jobs.retrieve.call(
                ResourceRetrieveRequest(JobIncludeFlags(), id),
                rpcClient
            ).orThrow().status.state

            delay(1000)
        }

        return lastKnownState
    }

    private data class TestContext(
        val parent: ResourceUsageTestContext,
        val collection: FileCollection,
        val jobId: String,
        val rpcClient: AuthenticatedClient,
        val currentState: JobState,
    )

    private suspend fun initializeTest(
        case: TestCase,
        product: Product.Compute,
        parameters: StartJobParameters,
        resourceInitialization: suspend ResourceUsageTestContext.(coll: FileCollection) -> List<AppParameterValue> = {
            emptyList()
        }
    ): TestContext {
        case.initialization()
        create()
        with(initializeResourceTestContext(case.products, emptyList())) {
            val rpcClient = adminClient.withProject(project)
            val (collection) = initializeCollection(project, rpcClient, case.storage)

            val resources = resourceInitialization(collection)

            val (id, lastKnownState) = startJob(parameters.copy(resources = resources), product, rpcClient)

            return TestContext(this, collection, id, rpcClient, lastKnownState)
        }
    }

    data class TestCase(
        val title: String,
        val initialization: suspend () -> Unit,
        val products: List<Product>,
        val storage: Product.Storage,
        val ingress: Product.Ingress?,
    )

    override fun defineTests() {
        val cases: List<TestCase> = listOfNotNull(
            runBlocking {
                // NOTE(Dan): Short guide on how to run Kubernetes tests.
                //
                // For the Kubernetes tests to run you must already have a running Kubernetes cluster with the
                // following requirements fulfilled:
                //
                //  - The system must already have Volcano installed
                //  - The Kubernetes cluster must share the temporary directory of this machine, since the
                //    "distributed" file system is created in "/tmp"
                //  - You must set a path to the kubeconfig file in the _INTEGRATION_ configuration (pass this via
                //    UCLOUD_INTEGRATION_CFG). If this is not set, then the test will refuse to run.
                //
                // Other things to be aware of:
                //
                //  - The Kubernetes cluster is cleaned at the beginning of each run
                //  - In practice this means that the app-kubernetes namespace is deleted...
                //  - ...and the persistent volume (+ claim) is recreated to match the integration test's storage

                val files = micro.configuration.requestChunkAtOrNull<String>("ceph", "cephfsBaseMount")
                val kubeConfig =
                    micro.configuration.requestChunkAtOrNull<String>("app", "kubernetes", "kubernetesConfig")

                if (files == null) {
                    log.warn("Don't know where files are located? This seems like a bug in the test suite.")
                    null
                } else if (kubeConfig == null) {
                    log.warn("Kubernetes configuration not supplied. UCloud/Compute tests will not run!")
                    null
                } else {
                    val k8 = KubernetesClient(KubernetesConfigurationSource.KubeConfigFile(kubeConfig, null))

                    // Check if volcano is present
                    run {
                        val hasVolcano = runCatching {
                            k8.getResource<Namespace>(KubernetesResources.namespaces.withName("volcano-system"))
                        }.isSuccess

                        if (!hasVolcano) {
                            log.warn("Volcano is not configured in the Kubernetes system. It must be installed first!")
                            return@runBlocking null
                        }
                    }

                    // Clean up from previous runs
                    run {
                        runCatching {
                            k8.deleteResource(KubernetesResources.namespaces.withName("app-kubernetes"))
                        }

                        runCatching {
                            k8.deleteResource(KubernetesResources.persistentVolumes.withName("storage"))
                        }

                        val deadline = Time.now() + 30_000
                        while (Time.now() < deadline) {
                            val hasNamespace = runCatching {
                                k8.getResource<Namespace>(KubernetesResources.namespaces.withName("app-kubernetes"))
                            }.isSuccess

                            if (!hasNamespace) break
                            delay(1000)
                        }
                    }

                    // Initialize basic resources required by the system
                    run {
                        k8.createResource(
                            KubernetesResources.namespaces,
                            defaultMapper.encodeToString(Namespace(metadata = ObjectMeta("app-kubernetes")))
                        )

                        k8.createResource(
                            KubernetesResources.persistentVolumes,
                            defaultMapper.encodeToString(
                                PersistentVolume(
                                    metadata = ObjectMeta("storage"),
                                    spec = PersistentVolume.Spec(
                                        capacity = JsonObject(
                                            mapOf(
                                                "storage" to JsonPrimitive("1000Gi")
                                            )
                                        ),
                                        accessModes = listOf("ReadWriteMany"),
                                        persistentVolumeReclaimPolicy = "Retain",
                                        storageClassName = "",
                                        hostPath = HostPathVolumeSource(path = files)
                                    )
                                )
                            )
                        )

                        k8.createResource(
                            KubernetesResources.persistentVolumeClaims.withNamespace("app-kubernetes"),
                            defaultMapper.encodeToString(
                                PersistentVolumeClaim(
                                    metadata = ObjectMeta("cephfs", "app-kubernetes"),
                                    spec = PersistentVolumeClaim.Spec(
                                        accessModes = listOf("ReadWriteMany"),
                                        storageClassName = "",
                                        volumeName = "storage",
                                        resources = Pod.Container.ResourceRequirements(
                                            requests = JsonObject(
                                                mapOf(
                                                    "storage" to JsonPrimitive("1000Gi")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }

                    val storageProduct = Product.Storage(
                        "u1-cephfs",
                        1L,
                        ProductCategoryId("u1-cephfs", UCLOUD_PROVIDER),
                        "Storage"
                    )

                    val projectHome = Product.Storage(
                        "project-home",
                        1L,
                        ProductCategoryId("u1-cephfs", UCLOUD_PROVIDER),
                        "storage"
                    )

                    val computeProduct = Product.Compute(
                        "u1-standard-1",
                        1L,
                        ProductCategoryId("u1-standard", UCLOUD_PROVIDER),
                        "Compute",
                        cpu = 1,
                        memoryInGigs = 1,
                        gpu = 0
                    )

                    val ingress = Product.Ingress(
                        "u1-publiclink",
                        1L,
                        ProductCategoryId("u1-publiclink", UCLOUD_PROVIDER),
                        "Description"
                    )

                    val products = listOf(storageProduct, projectHome, computeProduct, ingress)

                    TestCase(
                        "UCloud/Compute",
                        {
                            Products.create.call(BulkRequest(products), serviceClient).orThrow()
                        },
                        products,
                        storageProduct,
                        ingress
                    )
                }
            }
        )

        for (case in cases) {
            for (product in case.products.filterIsInstance<Product.Compute>()) {
                val titlePrefix = "Compute @ ${case.title} ($product):"
                test<Unit, Unit>("$titlePrefix Batch application") {
                    execute {
                        val ctx = initializeTest(
                            case,
                            product,
                            StartJobParameters(
                                longRunning = false,
                                interactivity = null,
                                waitForState = JobState.SUCCESS
                            ),
                        )

                        if (ctx.currentState != JobState.SUCCESS) {
                            throw IllegalStateException("Application did not succeed within deadline: ${ctx.currentState}")
                        }
                    }

                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Long running with cancel") {
                    execute {
                        val ctx = initializeTest(
                            case,
                            product,
                            StartJobParameters(
                                longRunning = true,
                                interactivity = null,
                                waitForState = JobState.RUNNING
                            )
                        )

                        if (ctx.currentState != JobState.RUNNING) {
                            throw IllegalStateException("Application did not start within deadline: ${ctx.currentState}")
                        }

                        Jobs.terminate.call(
                            bulkRequestOf(FindByStringId(ctx.jobId)),
                            ctx.rpcClient
                        ).orThrow()

                        val finalState = waitForState(ctx.jobId, JobState.SUCCESS, ctx.rpcClient)
                        if (finalState != JobState.SUCCESS) {
                            throw IllegalStateException("Application did not stop within deadline: $finalState")
                        }
                    }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Long running with cancel and follow") {
                    execute {
                        val ctx = initializeTest(
                            case,
                            product,
                            StartJobParameters(
                                longRunning = true,
                                interactivity = null,
                                waitForState = JobState.RUNNING
                            )
                        )

                        if (ctx.currentState != JobState.RUNNING) {
                            throw IllegalStateException("Application did not start within deadline: ${ctx.currentState}")
                        }

                        val wsClient = AuthenticatedClient(
                            micro.client,
                            OutgoingWSCall,
                            ctx.rpcClient.afterHook,
                            ctx.rpcClient.authenticator
                        )

                        var didSeeAnyMessages = false
                        coroutineScope {
                            val followJob = launch(Dispatchers.IO) {
                                Jobs.follow.subscribe(
                                    JobsFollowRequest(ctx.jobId),
                                    wsClient,
                                    handler = { event ->
                                        if (!didSeeAnyMessages && event.log.isNotEmpty()) {
                                            val anyMessage = event.log.any { it.stdout != null || it.stderr != null }
                                            if (anyMessage) {
                                                didSeeAnyMessages = true
                                            }
                                        }
                                    }
                                )
                            }

                            val deadlineJob = launch(Dispatchers.IO) {
                                delay(10_000)
                            }

                            select<Unit> {
                                followJob.onJoin {}
                                deadlineJob.onJoin {}
                            }

                            runCatching { followJob.cancel() }
                            runCatching { deadlineJob.cancel() }
                        }

                        assertThatInstance(didSeeAnyMessages, "we should have seen messages from the follow") { it }

                        Jobs.terminate.call(
                            bulkRequestOf(FindByStringId(ctx.jobId)),
                            ctx.rpcClient
                        ).orThrow()

                        val finalState = waitForState(ctx.jobId, JobState.SUCCESS, ctx.rpcClient)
                        if (finalState != JobState.SUCCESS) {
                            throw IllegalStateException("Application did not stop within deadline: $finalState")
                        }
                    }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Batch application with files") {
                    execute {
                        initializeTest(
                            case,
                            product,
                            StartJobParameters(
                                longRunning = false,
                                null,
                                waitForState = JobState.SUCCESS
                            ),
                            resourceInitialization = { coll ->
                                val inputFolder = "/${coll.id}/InputFolder"
                                Files.createFolder.call(
                                    bulkRequestOf(
                                        FilesCreateFolderRequestItem(inputFolder, WriteConflictPolicy.REJECT)
                                    ),
                                    adminClient.withProject(project)
                                )

                                listOf(AppParameterValue.File(inputFolder))
                            }
                        )
                    }

                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Batch application with license") {
                    execute { }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                if (case.ingress != null) {
                    test<Unit, Unit>("$titlePrefix Batch application with ingress") {
                        execute { }
                        case("No input") {
                            input(Unit)
                            check {}
                        }
                    }
                }

                test<Unit, Unit>("$titlePrefix Batch application with ip") {
                    execute { }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Create interactive shell") {
                    execute { }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Create interactive VNC") {
                    execute { }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }

                test<Unit, Unit>("$titlePrefix Create interactive web interface") {
                    execute { }
                    case("No input") {
                        input(Unit)
                        check {}
                    }
                }
            }
        }
    }
}
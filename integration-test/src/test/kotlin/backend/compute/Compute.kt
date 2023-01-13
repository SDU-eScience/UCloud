package dk.sdu.cloud.integration.backend.compute

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCreateFolderRequestItem
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.assertThatInstance
import dk.sdu.cloud.integration.backend.ResourceUsageTestContext
import dk.sdu.cloud.integration.backend.initializeResourceTestContext
import dk.sdu.cloud.integration.rpcClient
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

fun Product.toReference(): ProductReference = ProductReference(name, category.name, category.provider)

class ComputeTest : IntegrationTest() {
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
                    Pair(ApplicationTestData.figletBatch.metadata, mapOf("text" to AppParameterValue.Text("Hello, World!")))
                }

                longRunning && interactivity == null -> {
                    Pair(ApplicationTestData.figletLongRunning.metadata, emptyMap())
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
        ApplicationTestData.create()
        with(initializeResourceTestContext(case.products, emptyList())) {
            val rpcClient = piClient.withProject(project)
            val initializedCollection = initializeCollection(project, rpcClient, product.category.provider)
                ?: error("Unable to initialize drive")

            val resources = resourceInitialization(initializedCollection.collection)

            val (id, lastKnownState) = startJob(parameters.copy(resources = resources), product, rpcClient)

            return TestContext(this, initializedCollection.collection, id, rpcClient, lastKnownState)
        }
    }

    data class TestCase(
        val title: String,
        val products: List<Product>,
    )

    override fun defineTests() {
        testFilter = { a, b -> a.contains("slurm") }
        val cases: List<TestCase> = runBlocking {
            val allProducts = findProducts(findProviderIds())
            val productsByProviders = allProducts.groupBy { it.category.provider }

            productsByProviders.map { (provider, products) ->
                TestCase(
                    provider,
                    products
                )
            }
        }

        for (case in cases) {
            for (product in case.products.filterIsInstance<Product.Compute>()) {
                if (product.category.name == "syncthing") continue

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
                            rpcClient,
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
                                    piClient.withProject(project)
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

package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.IngressIncludeFlags
import dk.sdu.cloud.app.orchestrator.api.IngressSpecification
import dk.sdu.cloud.app.orchestrator.api.IngressState
import dk.sdu.cloud.app.orchestrator.api.Ingresses
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.DummyIngress
import dk.sdu.cloud.integration.DummyProvider
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.retrySection
import dk.sdu.cloud.provider.api.Permission
import kotlin.test.assertEquals

class IngressTest : IntegrationTest() {
    data class TestCase(
        val title: String,
        val initialization: suspend () -> Unit,
        val products: List<Product.Ingress>,
        val prefix: String,
        val suffix: String,
        val deadlineForReady: Int = 60 * 5,
        val ignoreReadyTest: Boolean = false,
    )

    override fun defineTests() {
        val cases = listOf(
            TestCase(
                "Dummy",
                { DummyProvider.initialize() },
                listOf(DummyIngress.perUnitIngress),
                "app-",
                "dummy.com",
                ignoreReadyTest = true
            ),
            run {
                val product = Product.Ingress(
                    "u1-publiclink",
                    1L,
                    ProductCategoryId("u1-publiclink", UCLOUD_PROVIDER),
                    "Description"
                )

                TestCase(
                    "UCloud/Compute",
                    { Products.create.call(bulkRequestOf(product), serviceClient).orThrow() },
                    listOf(product),
                    "app-",
                    ".cloud.sdu.dk"
                )
            }
        )

        for (case in cases) {
            resourceUsageTest(
                "Ingress @ ${case.title}",
                Ingresses,
                case.products,
                flagFactory = { flags ->
                    IngressIncludeFlags(
                        includeOthers = flags.includeOthers,
                        includeUpdates = flags.includeUpdates,
                        includeSupport = flags.includeSupport,
                        includeProduct = flags.includeProduct,
                        filterCreatedBy = flags.filterCreatedBy,
                        filterCreatedAfter = flags.filterCreatedAfter,
                        filterCreatedBefore = flags.filterCreatedBefore,
                        filterProvider = flags.filterProvider,
                        filterProductId = flags.filterProductId,
                        filterProductCategory = flags.filterProductCategory,
                        filterProviderIds = flags.filterProviderIds,
                        filterIds = flags.filterIds,
                        hideProductId = flags.hideProductId,
                        hideProductCategory = flags.hideProductCategory,
                        hideProvider = flags.hideProvider,
                    )
                },
                initialization = case.initialization,
                additionalTesting = { input, resources ->
                    if (!case.ignoreReadyTest) {
                        val filterId = resources.joinToString(",") { it.id }
                        retrySection(case.deadlineForReady, delay = 1000L) {
                            val items = Ingresses.browse.call(
                                ResourceBrowseRequest(IngressIncludeFlags(filterIds = filterId)),
                                adminClient.withProject(project)
                            ).orThrow().items

                            val expectedSize = resources.size - input.delete.count { it }
                            assertEquals(expectedSize, items.size)

                            val allReady = items.all { it.status.state == IngressState.READY }
                            if (!allReady) throw IllegalStateException("Ingresses are not ready yet: $items")
                        }
                    }
                },
                caseBuilder = {
                    for (product in case.products) {
                        for (count in 1..3) {
                            for (userType in UserType.values()) {
                                case("Simple test: $userType (count = $count)") {
                                    input(
                                        ResourceUsageTestInput(
                                            (0 until count).map { idx ->
                                                IngressSpecification(
                                                    "${case.prefix}testing-${idx}${case.suffix}",
                                                    product.toReference()
                                                )
                                            },
                                            creator = userType
                                        )
                                    )

                                    check {
                                        // All good
                                    }
                                }
                            }
                        }

                        case("Permission updates") {
                            input(
                                ResourceUsageTestInput(
                                    listOf(
                                        IngressSpecification(
                                            "${case.prefix}testing${case.suffix}",
                                            product.toReference()
                                        )
                                    ),
                                    listOf("G1"),
                                    listOf(
                                        PartialAclUpdate(
                                            listOf(GroupAndPermission("G1", listOf(Permission.READ))),
                                            emptyList()
                                        )
                                    )
                                )
                            )

                            check {}
                        }

                        case("Deletion") {
                            input(
                                ResourceUsageTestInput(
                                    listOf(
                                        IngressSpecification(
                                            "${case.prefix}testing${case.suffix}",
                                            product.toReference()
                                        )
                                    ),
                                    delete = listOf(true)
                                )
                            )

                            check {}
                        }
                    }
                }
            )
        }
    }
}

package dk.sdu.cloud.integration.backend.compute

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.backend.*
import dk.sdu.cloud.integration.utils.*
import dk.sdu.cloud.provider.api.Permission
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

class IngressTest : IntegrationTest() {
    data class TestCase(
        val title: String,
        val products: List<Product.Ingress>,
        val deadlineForReady: Int = 60 * 5,
    )

    override fun defineTests() {
        val cases: List<TestCase> = runBlocking {
            val allProducts = findProducts()
            val productsByProviders = allProducts.groupBy { it.category.provider }

            productsByProviders.mapNotNull { (provider, products) ->
                val ingresses = products.filterIsInstance<Product.Ingress>()
                if (ingresses.isEmpty()) null
                else TestCase(provider, ingresses)
            }
        }

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
                additionalTesting = { input, resources ->
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
                },
                caseBuilder = {
                    for (product in case.products) {
                        for (count in 1..3) {
                            for (userType in UserType.values()) {
                                if (userType != UserType.SOME_OTHER_USER) {
                                    case("Simple test: $userType (count = $count)") {
                                        input(
                                            ResourceUsageTestInput(
                                                specs = {
                                                    val support = findSupport<Product.Ingress, IngressSupport>(
                                                        product,
                                                        project, client(userType)
                                                    ).support

                                                    val prefix = support.domainPrefix
                                                    val suffix = support.domainSuffix

                                                    (0 until count).map { idx ->
                                                        IngressSpecification(
                                                            "${prefix}${generateId("test")}${suffix}",
                                                            product.toReference()
                                                        )
                                                    }
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
                        }

                        case("Permission updates") {
                            input(
                                ResourceUsageTestInput(
                                    specs = {
                                        val support = findSupport<Product.Ingress, IngressSupport>(product,
                                            project, client(UserType.PI)).support

                                        listOf(
                                            IngressSpecification(
                                                support.domainPrefix + generateId("test") + support.domainSuffix,
                                                product.toReference()
                                            )
                                        )
                                    },
                                    listOf("G1"),
                                    listOf(
                                        PartialAclUpdate(
                                            listOf(GroupAndPermission("G1", listOf(Permission.READ))),
                                            emptyList()
                                        )
                                    )
                                )
                            )

                            expectSuccess()
                        }


                        case("Deletion") {
                            input(
                                ResourceUsageTestInput(
                                    specs = {
                                        val support = findSupport<Product.Ingress, IngressSupport>(product,
                                            project, client(UserType.PI)).support

                                        listOf(
                                            IngressSpecification(
                                                support.domainPrefix + generateId("test") + support.domainSuffix,
                                                product.toReference()
                                            )
                                        )
                                    },
                                    delete = listOf(true)
                                )
                            )

                            expectSuccess()
                        }
                    }
                }
            )
        }
    }
}

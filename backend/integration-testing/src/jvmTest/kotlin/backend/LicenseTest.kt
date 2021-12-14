package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.*
import dk.sdu.cloud.provider.api.Permission
import kotlin.test.assertEquals

class LicenseTest : IntegrationTest() {
    data class TestCase(
        val title: String,
        val initialization: suspend () -> Unit,
        val products: List<Product.License>,
        val deadlineForReady: Int = 60 * 5,
        val ignoreReadyTest: Boolean = false,
    )

    override fun defineTests() {
        val cases = listOf(
            TestCase(
                "Dummy",
                { DummyProvider.initialize() },
                listOf(DummyLicense.license),
                ignoreReadyTest = true
            ),
        )

        for (case in cases) {
            resourceUsageTest(
                "Licenses @ ${case.title}",
                Licenses,
                case.products,
                flagFactory = { flags ->
                    LicenseIncludeFlags(
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
                            val items = Licenses.browse.call(
                                ResourceBrowseRequest(LicenseIncludeFlags(filterIds = filterId)),
                                adminClient.withProject(project)
                            ).orThrow().items

                            val expectedSize = resources.size - input.delete.count { it }
                            assertEquals(expectedSize, items.size)

                            val allReady = items.all { it.status.state == LicenseState.READY }
                            if (!allReady) throw IllegalStateException("Licenses are not ready yet: $items")
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
                                                LicenseSpecification(product.toReference())
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
                                    listOf(LicenseSpecification(product.toReference())),
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
                                    listOf(LicenseSpecification(product.toReference())),
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

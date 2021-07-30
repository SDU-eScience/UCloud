package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.checkMinimumValue
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.adminClient
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.service.PageV2
import org.elasticsearch.client.Requests.bulkRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

val sampleIngress = Product.Ingress(
    "u1-ingress",
    0,
    ProductCategoryId("ingress", UCLOUD_PROVIDER)
)

val sampleCompute = Product.Compute(
    "u1-standard-1",
    100_000,
    ProductCategoryId("standard", UCLOUD_PROVIDER),
    cpu = 1,
    memoryInGigs = 1
)

val sampleStorage = Product.Storage(
    "u1-cephfs",
    100_000,
    ProductCategoryId("cephfs", UCLOUD_PROVIDER)
)

val sampleNetworkIp = Product.NetworkIP(
    "u1-public-ip",
    1_000_000,
    ProductCategoryId("public-ip", UCLOUD_PROVIDER)
)

val sampleProducts = listOf(sampleCompute, sampleStorage, sampleIngress, sampleNetworkIp)

suspend fun createProvider(providerName: String = UCLOUD_PROVIDER) {
    Providers.create.call(
        bulkRequestOf(
            ProviderSpecification(
                providerName,
                "localhost",
                https = false,
                port = 8080
            )
        ),
        adminClient
    ).orThrow()
}

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts() {
    createProvider()

    Products.create.call(
        BulkRequest(sampleProducts),
        serviceClient
    ).orThrow()
}

fun Product.toReference(): ProductReference = ProductReference(name, category.name, category.provider)

class ProductTest : IntegrationTest() {
    override fun defineTests() {
        run {
            class In(
                val createZeroProducts: Boolean = false,
                val createSingleProduct: Boolean = false,
                val createMultipleProducts: Boolean = false,
                val request: ProductsBrowseRequest = ProductsBrowseRequest(),
                val createWallet: Boolean = true
            )

            class Out(
                val page: PageV2<Product>
            )

            test<In, Out>("Browsing products") {
                execute {
                    when {
                        input.createZeroProducts -> {
                            //Do not create any products
                        }
                        input.createSingleProduct -> {
                            createProvider()
                            Products.create.call(
                                BulkRequest(listOf(sampleCompute)),
                                serviceClient
                            )
                        }
                        input.createMultipleProducts -> {
                            createSampleProducts()
                        }
                    }
                    val client: AuthenticatedClient
                    if (input.request.includeBalance == true) {
                        val createdUser = createUser("${title}_$testId")
                        client = createdUser.client
                        if (input.createWallet) {
                            val owner = WalletOwner.User(createdUser.username)
                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleStorage.category,
                                        owner,
                                        1000000,
                                        "Initial deposit"
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                        }
                    } else {
                        client = adminClient
                    }

                    val products = Products.browse.call(
                        input.request,
                        client
                    ).orThrow()

                    Out(page = products)
                }


                case("zero") {
                    input(In(createZeroProducts = true))
                    check { assertEquals(output.page.items.size, 0) }
                }

                case("single") {
                    input(In(createSingleProduct = true))
                    check { assertEquals(output.page.items.size, 1) }
                }

                case("multiple") {
                    input(In(createMultipleProducts = true))
                    check {
                        assertEquals(output.page.items.size, sampleProducts.size)
                        assertNull(output.page.items.first().balance)
                    }
                }

                case("include balance") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                includeBalance = true
                            )
                        )
                    )
                    check {
                        assertEquals(sampleProducts.size, output.page.items.size)

                        val first = output.page.items.first()
                        assertNotNull(first.balance)
                        assertEquals(1000000, first.balance)
                        assertEquals(sampleStorage, first)

                        val last = output.page.items.last()
                        assertNotNull(last.balance)
                        assertEquals(0, last.balance)
                        assertEquals(sampleCompute, last)
                    }
                }

                case("include balance but no wallet") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                includeBalance = true
                            ),
                            createWallet = false
                        )
                    )
                    check {
                        assertEquals(sampleProducts.size, output.page.items.size)

                        val first = output.page.items.first()
                        assertNotNull(first.balance)
                        assertEquals(0, first.balance)
                        assertEquals(sampleStorage, first)

                        val last = output.page.items.last()
                        assertNotNull(last.balance)
                        assertEquals(0, last.balance)
                        assertEquals(sampleCompute, last)
                    }
                }

                case("filter by provider") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterProvider = sampleNetworkIp.category.provider
                            )
                        )
                    )
                    check {
                        assertEquals(4, output.page.items.size)
                        assertTrue(output.page.items.contains(sampleCompute))
                        assertTrue(output.page.items.contains(sampleIngress))
                        assertTrue(output.page.items.contains(sampleNetworkIp))
                        assertTrue(output.page.items.contains(sampleStorage))
                    }
                }

                case("filter by category") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterCategory = sampleIngress.category.name
                            )
                        )
                    )
                    check {
                        assertEquals(1, output.page.items.size)
                        assertEquals(sampleIngress, output.page.items.first())
                    }
                }

                case("filter by name") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterName = sampleCompute.name
                            )
                        )
                    )
                    check {
                        assertEquals(1, output.page.items.size)
                        assertEquals(sampleCompute, output.page.items.first())
                    }
                }

                case("filter by product type") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterArea = ProductType.STORAGE
                            )
                        )
                    )
                    check {
                        assertEquals(1, output.page.items.size)
                        assertEquals(sampleStorage, output.page.items.first())
                    }
                }

                case("bad filter name") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterName = "something Wrong"
                            )
                        )
                    )
                    check {
                        assertEquals(0, output.page.items.size)
                    }
                }

                case("bad filter category") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterCategory = "something Wrong"
                            )
                        )
                    )
                    check {
                        assertEquals(0, output.page.items.size)
                    }
                }

                case("bad filter provider") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterProvider = "something wrong"
                            )
                        )
                    )
                    check {
                        assertEquals(0, output.page.items.size)
                    }
                }
            }
        }
        run {
            class In(
                val request: ProductsRetrieveRequest,
            )

            class Out(
                val product: Product
            )

            test<In, Out>("retrieving products") {
                execute {
                    createSampleProducts()

                    val client: AuthenticatedClient

                    if (input.request.includeBalance == true) {
                        val createdUser = createUser("${title}_$testId")
                        client = createdUser.client
                        val owner = WalletOwner.User(createdUser.username)
                        Accounting.rootDeposit.call(
                            bulkRequestOf(
                                RootDepositRequestItem(
                                    sampleStorage.category,
                                    owner,
                                    1000000,
                                    "Initial deposit"
                                )
                            ),
                            serviceClient
                        ).orThrow()
                    } else {
                        client = adminClient
                    }

                    val product = Products.retrieve.call(
                        input.request,
                        client
                    ).orThrow()

                    Out(product = product)
                }

                case("retrieve without filter") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleIngress.name,
                                filterCategory = sampleIngress.category.name,
                                filterProvider = sampleIngress.category.provider
                            )
                        )
                    )
                    check {
                        assertEquals(sampleIngress, output.product)
                    }
                }
            }
        }
    }
    /*
    @Test
    fun `test product create, read and update`() = t {
        run {
            // Test conditions before create
            assertThatInstance(
                Products.findProduct.call(
                    FindProductRequest(sampleCompute.category.provider, sampleCompute.category.name, sampleCompute.name),
                    serviceClient
                ),
                "fails because the product doesn't exist"
            ) { !it.statusCode.isSuccess() }

            assertThatInstance(
                Products.listProducts.call(
                    ListProductsRequest(sampleCompute.category.provider),
                    serviceClient
                ).orThrow(),
                "has no items"
            ) { it.items.isEmpty() }

            assertThatInstance(
                Products.listProductsByType.call(
                    ListProductsByAreaRequest(sampleCompute.category.provider, ProductArea.COMPUTE),
                    serviceClient
                ).orThrow(),
                "has no items"
            ) { it.items.isEmpty() }

            assertThatInstance(
                Products.retrieveAllFromProvider.call(
                    RetrieveAllFromProviderRequest(sampleCompute.category.provider),
                    serviceClient
                ).orThrow(),
                "has no items"
            ) { it.isEmpty() }
        }

        Products.createProduct.call(sampleCompute, serviceClient).orThrow()

        run {
            // Test conditions after create
            assertEquals(
                sampleCompute,
                Products.findProduct.call(
                    FindProductRequest(sampleCompute.category.provider, sampleCompute.category.name, sampleCompute.name),
                    serviceClient
                ).orThrow()
            )

            assertThatInstance(
                Products.listProducts.call(
                    ListProductsRequest(sampleCompute.category.provider),
                    serviceClient
                ).orThrow(),
                "has our new product"
            ) { it.items.single() == sampleCompute }

            assertThatInstance(
                Products.listProductsByType.call(
                    ListProductsByAreaRequest(sampleCompute.category.provider, ProductArea.COMPUTE),
                    serviceClient
                ).orThrow(),
                "has our new product"
            ) { it.items.single() == sampleCompute }

            assertThatInstance(
                Products.retrieveAllFromProvider.call(
                    RetrieveAllFromProviderRequest(sampleCompute.category.provider),
                    serviceClient
                ).orThrow(),
                "has our new product"
            ) { it.single() == sampleCompute }
        }

        val newProduct = sampleCompute.copy(cpu = 42)
        Products.updateProduct.call(newProduct, serviceClient).orThrow()

        assertEquals(
            newProduct,
            Products.findProduct.call(
                FindProductRequest(sampleCompute.category.provider, sampleCompute.category.name, sampleCompute.name),
                serviceClient
            ).orThrow()
        )
    }

    @Test
    fun `test finding a storage product`() = t {
        Products.createProduct.call(sampleStorage, serviceClient).orThrow()
        assertEquals(
            sampleStorage,
            Products.findProduct.call(
                FindProductRequest(sampleStorage.category.provider, sampleStorage.category.name, sampleStorage.name),
                serviceClient
            ).orThrow()
        )
    }

    @Test
    fun `test permissions`() = t {
        createSampleProducts()
        val normalUser = createUser()
        assertThatInstance(
            Products.updateProduct.call(
                sampleCompute.copy(cpu = 42),
                normalUser.client
            ),
            "fails"
        ) { !it.statusCode.isSuccess() }

        assertThatInstance(
            Products.createProduct.call(
                sampleCompute.copy(name = "qweasd"),
                normalUser.client
            ),
            "fails"
        ) { !it.statusCode.isSuccess() }

        assertThatInstance(
            Products.retrieveAllFromProvider.call(
                RetrieveAllFromProviderRequest(sampleCompute.category.provider),
                normalUser.client
            ).orThrow(),
            "contains the sample products"
        ) { it.containsAll(sampleProducts) }
    }

    @Test
    fun `test area constraint`() = t {
        // All products in a given category must be from the same area. This test makes sure that we cannot
        // suddenly change.

        Products.createProduct.call(sampleCompute, serviceClient).orThrow()
        assertThatInstance(
            Products.createProduct.call(
                sampleStorage.copy(name = "notacomputeproduct", category = sampleCompute.category),
                serviceClient
            ),
            "fails because we changed area"
        ) { it.statusCode == HttpStatusCode.BadRequest }

        assertThatInstance(
            Products.updateProduct.call(
                sampleStorage.copy(name = sampleCompute.name, category = sampleCompute.category),
                serviceClient
            ),
            "fails"
        ) { !it.statusCode.isSuccess() }
    }

    @Test
    fun `test updating product which doesn't exist`() = t {
        assertThatInstance(
            Products.updateProduct.call(sampleCompute, serviceClient),
            "fails"
        ) { !it.statusCode.isSuccess() }
    }

     */
}

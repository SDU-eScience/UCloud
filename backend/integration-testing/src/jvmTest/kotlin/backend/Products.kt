package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.checkMinimumValue
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.adminClient
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.test.assertThatInstance
import org.elasticsearch.client.Requests.bulkRequest
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

const val OTHER_PROVIDER = "NewProvider"

val sampleIngress = Product.Ingress(
    "u1-ingress",
    1,
    ProductCategoryId("ingress", UCLOUD_PROVIDER),
    description = "product description"
)

val sampleCompute = Product.Compute(
    "u1-standard-1",
    100_000,
    ProductCategoryId("standard", UCLOUD_PROVIDER),
    cpu = 1,
    memoryInGigs = 1,
    description = "product description"
)

val sampleCompute2 = sampleCompute.copy(
    name = "u1-standard-2",
    cpu = 2,
    memoryInGigs = 2
)

val sampleCompute3 = sampleCompute.copy(
    name = "u1-standard-3",
    cpu = 3,
    memoryInGigs = 3
)

val sampleCompute4 = sampleCompute.copy(
    name = "u1-standard-4",
    cpu = 4,
    memoryInGigs = 4
)

val sampleCompute5 = sampleCompute.copy(
    name = "u1-standard-5",
    cpu = 5,
    memoryInGigs = 5
)

val sampleComputeOtherProvider = sampleCompute.copy(
    category = ProductCategoryId("standard", OTHER_PROVIDER)
)

val sampleStorageDifferential = Product.Storage(
    "u1-cephfs-quota",
    1L,
    ProductCategoryId("cephfs-quota", UCLOUD_PROVIDER),
    chargeType = ChargeType.DIFFERENTIAL_QUOTA,
    description = "product description",
    unitOfPrice = ProductPriceUnit.PER_UNIT
)

val sampleStorage = Product.Storage(
    "u1-cephfs",
    100_000,
    ProductCategoryId("cephfs", UCLOUD_PROVIDER),
    description = "product description"
)

val sampleStorage2 = sampleStorage.copy(
    name = "u2-cephfs"
)

val sampleStorageOtherProvider = sampleStorage.copy(
    category = ProductCategoryId("cephfs", OTHER_PROVIDER)
)

val sampleNetworkIp = Product.NetworkIP(
    "u1-public-ip",
    1,
    ProductCategoryId("public-ip", UCLOUD_PROVIDER),
    description = "product description"
)

val sampleProducts = listOf(sampleCompute, sampleStorage, sampleIngress, sampleNetworkIp, sampleStorageDifferential,
    sampleCompute2, sampleCompute3, sampleCompute4, sampleCompute5)
val sampleProductsOtherProvider = listOf(sampleComputeOtherProvider, sampleStorageOtherProvider)

suspend fun createProvider(providerName: String = UCLOUD_PROVIDER) {
    if (providerName == "ucloud" || providerName == "dummy") {
        println("ucloud and dummy provider initiated by launcher")
    } else {
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
}

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts() {
    try {
        createProvider()
    } catch (ex: RPCException) {
        if (ex.httpStatusCode == HttpStatusCode.Conflict) {
            println("Provider already exists")
        }
        else {
            throw ex
        }
    }
    for (product in sampleProducts) {
        Products.create.call(
            BulkRequest(listOf(product)),
            serviceClient
        ).orThrow()
    }
}

suspend fun createAdditionalProductsFromUcloudProvider() {
    Products.create.call(
        bulkRequestOf(
            sampleCompute2,
            sampleStorage2
        ),
        serviceClient
    ).orThrow()
}

suspend fun createSampleProductsOtherProvider() {
    createProvider(providerName = OTHER_PROVIDER)

    Products.create.call(
        BulkRequest(sampleProductsOtherProvider),
        serviceClient
    ).orThrow()
}

fun Product.toReference(): ProductReference = ProductReference(name, category.name, category.provider)

class ProductTest : IntegrationTest() {
    override fun defineTests() {

        run {
            class In(
                val products: List<Product>
            )

            class Out(
                val page: PageV2<Product>
            )

            test<In, Out>("Create tests") {
                execute {
                    createProvider("ucloud")
                    Products.create.call(
                        bulkRequestOf(
                            *input.products.toTypedArray()
                        ),
                        serviceClient
                    ).orThrow()

                    Out(
                        Products.browse.call(
                            ProductsBrowseRequest(),
                            adminClient
                        ).orThrow()
                    )
                }

                case("Same product category products insert") {
                    input(
                        In(
                            listOf(
                                Product.Compute(
                                    "standard",
                                    20L,
                                    ProductCategoryId(
                                        "ucloud", "ucloud"
                                    ),
                                    description = "description",
                                    unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE
                                ),
                                Product.Compute(
                                    "standard2",
                                    40L,
                                    ProductCategoryId(
                                        "ucloud", "ucloud"
                                    ),
                                    description = "description",
                                    unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE
                                )
                            )
                        )
                    )
                    check {
                        assertEquals(2, output.page.items.size)
                    }
                }

                case("different product category products insert") {
                    input(
                        In(
                            listOf(
                                Product.Compute(
                                    "standard",
                                    20L,
                                    ProductCategoryId(
                                        "ucloud", "ucloud"
                                    ),
                                    description = "description"
                                ),
                                Product.Compute(
                                    "standard2",
                                    40L,
                                    ProductCategoryId(
                                        "ucloud", "ucloud"
                                    ),
                                    description = "description",
                                    chargeType = ChargeType.DIFFERENTIAL_QUOTA,
                                    unitOfPrice = ProductPriceUnit.PER_UNIT
                                )
                            )
                        )
                    )
                    expectStatusCode(HttpStatusCode.BadRequest)
                }
            }


        }
        run {
            class In(
                val createZeroProducts: Boolean = false,
                val createSingleProduct: Boolean = false,
                val createMultipleProducts: Boolean = false,
                val createMultipleVersions: Boolean = false,
                val request: ProductsBrowseRequest = ProductsBrowseRequest(),
                val createWallet: Boolean = true,
                val additionalProvider: Boolean = false
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
                        input.createMultipleVersions -> {
                            createProvider()
                            Products.create.call(
                                BulkRequest(listOf(sampleCompute)),
                                serviceClient
                            )
                            Products.create.call(
                                BulkRequest(listOf(sampleCompute.copy(pricePerUnit = 20.DKK))),
                                serviceClient
                            )
                            Products.create.call(
                                BulkRequest(listOf(sampleCompute.copy(pricePerUnit = 50.DKK))),
                                serviceClient
                            )
                            Products.create.call(
                                BulkRequest(listOf(sampleCompute.copy(pricePerUnit = 10.DKK))),
                                serviceClient
                            )
                        }
                    }
                    if (input.additionalProvider) {
                        createSampleProductsOtherProvider()
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
                                        "Initial deposit",
                                        transactionId = UUID.randomUUID().toString()
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

                case("multiple versions") {
                    input(In(createMultipleVersions = true))
                    check {
                        assertEquals(1, output.page.items.size)
                        assertNull(output.page.items.first().balance)
                        assertEquals(4, output.page.items.first().version)
                        assertEquals(10.DKK, output.page.items.first().pricePerUnit)
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
                        assertEquals(1, output.page.items.size)

                        val first = output.page.items.first()
                        assertNotNull(first.balance)
                        assertEquals(1000000, first.balance)
                        assertEquals(sampleStorage, first)
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
                        assertEquals(sampleProducts.size, output.page.items.size)
                        assertTrue(output.page.items.contains(sampleCompute))
                        assertTrue(output.page.items.contains(sampleIngress))
                        assertTrue(output.page.items.contains(sampleNetworkIp))
                        assertTrue(output.page.items.contains(sampleStorage))
                    }
                }

                case("filter by provider, multiple providers $UCLOUD_PROVIDER") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterProvider = sampleNetworkIp.category.provider
                            ),
                            additionalProvider = true
                        )
                    )
                    check {
                        assertEquals(sampleProducts.size, output.page.items.size)
                        assertTrue(output.page.items.contains(sampleCompute))
                        assertTrue(output.page.items.contains(sampleIngress))
                        assertTrue(output.page.items.contains(sampleNetworkIp))
                        assertTrue(output.page.items.contains(sampleStorage))
                    }
                }

                case("filter by provider, multiple providers $OTHER_PROVIDER") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterProvider = sampleComputeOtherProvider.category.provider
                            ),
                            additionalProvider = true
                        )
                    )
                    check {
                        assertEquals(sampleProductsOtherProvider.size, output.page.items.size)
                        assertTrue(output.page.items.contains(sampleComputeOtherProvider))
                        assertTrue(output.page.items.contains(sampleStorageOtherProvider))
                    }
                }

                case("filter by provider and wrong name, multiple providers $OTHER_PROVIDER") {
                    input(
                        In(
                            createMultipleProducts = true,
                            request = ProductsBrowseRequest(
                                filterProvider = sampleComputeOtherProvider.category.provider,
                                filterName = "cephfs"
                            ),
                            additionalProvider = true
                        )
                    )
                    check {
                        assertEquals(0, output.page.items.size)
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
                        assertEquals(2, output.page.items.size)
                        assertTrue(output.page.items.contains(sampleStorageDifferential))
                        assertTrue(output.page.items.contains(sampleStorage))
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
                val createWallet: Boolean = true,
                val createMultipleVersions: Boolean = false
            )

            class Out(
                val product: Product
            )

            test<In, Out>("retrieving products") {
                execute {
                    createSampleProducts()

                    if (input.createMultipleVersions) {
                        createSampleProducts()
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
                                        "Initial deposit",
                                        transactionId = UUID.randomUUID().toString()
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                        }
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
                        //simple balance null check
                        assertNull(output.product.balance)
                    }
                }

                case("retrieve non-existing (name) without filter") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = "not name",
                                filterCategory = sampleIngress.category.name,
                                filterProvider = sampleIngress.category.provider
                            )
                        )
                    )
                    expectStatusCode(HttpStatusCode.NotFound)
                }

                case("retrieve non-existing (category) without filter") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleIngress.name,
                                filterCategory = "sampleIngress.category.name",
                                filterProvider = sampleIngress.category.provider
                            )
                        )
                    )
                    expectStatusCode(HttpStatusCode.NotFound)
                }

                case("retrieve non-existing (provider) without filter") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleIngress.name,
                                filterCategory = sampleIngress.category.name,
                                filterProvider = "sampleIngress.category.provider"
                            )
                        )
                    )
                    expectStatusCode(HttpStatusCode.NotFound)
                }

                case("retrieve with filter (productType)") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleCompute.name,
                                filterCategory = sampleCompute.category.name,
                                filterProvider = sampleCompute.category.provider,
                                filterArea = ProductType.COMPUTE
                            )
                        )
                    )
                    check {
                        assertEquals(sampleCompute, output.product)
                    }
                }

                case("retrieve with wrong (productType) filter") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleIngress.name,
                                filterCategory = "sampleIngress.category.name",
                                filterProvider = sampleIngress.category.provider,
                                filterArea = ProductType.STORAGE
                            )
                        )
                    )
                    expectStatusCode(HttpStatusCode.NotFound)
                }

                case("retrieve with filter (productType) and include balance") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleStorage.name,
                                filterCategory = sampleStorage.category.name,
                                filterProvider = sampleStorage.category.provider,
                                filterArea = ProductType.STORAGE,
                                includeBalance = true
                            )
                        )
                    )
                    check {
                        assertEquals(1000000, output.product.balance)
                    }
                }

                case("retrieve with filter (version)") {
                    input(
                        In(
                            ProductsRetrieveRequest(
                                filterName = sampleCompute.name,
                                filterCategory = sampleCompute.category.name,
                                filterProvider = sampleCompute.category.provider,
                                filterVersion = 1
                            ),
                            createMultipleVersions = true
                        )
                    )
                    check {
                        assertEquals(sampleCompute, output.product)
                        assertEquals(1, output.product.version)
                    }
                }
            }
        }

        run {
            class In(
                val browseRequest: ProductsBrowseRequest? = null,
                val retrieveRequest: ProductsRetrieveRequest? = null,
                val createWallet: Boolean = false
            )

            class Out(
                val product: Product?,
                val page: PageV2<Product>?
            )
            
            test<In, Out>("browsing and retrieving newest versions") {
                execute {
                    createSampleProducts()
                    Products.create.call(
                        BulkRequest(
                            listOf(
                                sampleStorage.copy(pricePerUnit = 1),
                                sampleCompute.copy(pricePerUnit = 333),
                                sampleIngress.copy(pricePerUnit = 1),
                                sampleNetworkIp.copy(pricePerUnit = 1),
                                sampleStorageDifferential.copy(description = "new")
                            )
                        ),
                        serviceClient
                    ).orThrow()

                    val client: AuthenticatedClient

                    if (input.browseRequest?.includeBalance == true
                        || input.retrieveRequest?.includeBalance == true) {
                        val createdUser = createUser("${title}_$testId")
                        client = createdUser.client
                        if (input.createWallet) {
                            val owner = WalletOwner.User(createdUser.username)
                            Accounting.rootDeposit.call(
                                bulkRequestOf(
                                    RootDepositRequestItem(
                                        sampleCompute.category,
                                        owner,
                                        1000000,
                                        "Initial deposit",
                                        transactionId = UUID.randomUUID().toString()
                                    )
                                ),
                                serviceClient
                            ).orThrow()
                        }
                    } else {
                        client = adminClient
                    }

                    var retrieveResult: Product? = null
                    var browseResult: PageV2<Product>? = null
                    if (input.retrieveRequest != null) {
                        val request = input.retrieveRequest!!
                        retrieveResult = Products.retrieve.call(
                            request,
                            client
                        ).orThrow()
                    }
                    if (input.browseRequest != null) {
                        val request = input.browseRequest!!
                        browseResult = Products.browse.call(
                            request,
                            client
                        ).orThrow()
                    }

                    Out(product = retrieveResult, page = browseResult)
                }

                case("browse without filters") {
                    input(
                        In(
                            browseRequest = ProductsBrowseRequest()
                        )
                    )
                    check {
                        assertNotNull(output.page)
                        assertEquals(sampleProducts.associateBy{ it.category}.size, output.page!!.items.size)
                    }
                }

                case("browse with balance") {
                    input(
                        In(
                            browseRequest = ProductsBrowseRequest(
                                filterName = sampleCompute.name,
                                includeBalance = true
                            ),
                            createWallet = true
                        )
                    )
                    check {
                        assertNotNull(output.page)
                        val page = output.page!!
                        assertEquals(1, page.items.size)
                        val item = page.items.first()
                        assertEquals(1000000, item.balance)
                        assertEquals(sampleCompute.name, item.name)
                        assertEquals(2, item.version)
                    }
                }
                
                case("browse all versions") {
                    input(
                        In(
                            browseRequest = ProductsBrowseRequest(
                                showAllVersions = true,
                            )
                        )
                    )
                    
                    check {
                        assertNotNull(output.page)
                        val page = output.page!!
                        assertEquals(sampleProducts.size + 5, page.items.size)
                        sampleProducts.forEach { product ->
                            assertTrue(page.items.contains(product))
                        }
                        assertTrue(page.items.contains(sampleStorage.copy(pricePerUnit = 1, version = 2)))
                        assertTrue(page.items.contains(sampleCompute.copy(pricePerUnit = 333, version = 2)))
                        assertTrue(page.items.contains(sampleIngress.copy(pricePerUnit = 1, version = 2)))
                        assertTrue(page.items.contains(sampleNetworkIp.copy(pricePerUnit = 1, version = 2)))
                        assertTrue(page.items.contains(sampleStorageDifferential.copy(description = "new", version = 2)))
                    }
                }

                case("retrieve without additional filters") {
                    input(
                        In(
                            retrieveRequest = ProductsRetrieveRequest(
                                filterName = sampleCompute.name,
                                filterCategory = sampleCompute.category.name,
                                filterProvider = sampleCompute.category.provider,
                            )
                        )
                    )
                    check {
                        assertNotNull(output.product)
                        val product = output.product!!
                        assertEquals(sampleCompute.copy(pricePerUnit = 333, version = 2), product)
                    }
                }

                case("retrieve with balance") {
                    input(
                        In(
                            retrieveRequest = ProductsRetrieveRequest(
                                filterName = sampleCompute.name,
                                filterCategory = sampleCompute.category.name,
                                filterProvider = sampleCompute.category.provider,
                                includeBalance = true
                            ),
                            createWallet = true
                        )
                    )
                    check {
                        assertNotNull(output.product)
                        val product = output.product!!
                        assertEquals(1000000, product.balance)

                        assertEquals(sampleCompute.name, product.name)
                        assertEquals(2, product.version)
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

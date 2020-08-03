package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.junit.Test
import kotlin.test.assertEquals

val sampleCompute = Product.Compute(
    "u1-standard-1",
    100_000,
    ProductCategoryId("standard", UCLOUD_PROVIDER),
    cpu = 1,
    memoryInGigs = 4
)

val sampleStorage = Product.Storage(
    "u1-cephfs",
    100_000,
    ProductCategoryId("cephfs", UCLOUD_PROVIDER)
)

val sampleProducts = listOf(sampleCompute, sampleStorage)

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts() {
    sampleProducts.forEach { product ->
        Products.createProduct.call(
            product,
            serviceClient
        ).orThrow()
    }
}

class ProductTest : IntegrationTest() {
    @Test
    fun `test product cru`() = t {
        run {
            // Test conditions before create
            assertThatInstance(
                Products.findProduct.call(
                    FindProductRequest(sampleCompute.category.provider, sampleCompute.category.id, sampleCompute.id),
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
                    FindProductRequest(sampleCompute.category.provider, sampleCompute.category.id, sampleCompute.id),
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
                FindProductRequest(sampleCompute.category.provider, sampleCompute.category.id, sampleCompute.id),
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
                FindProductRequest(sampleStorage.category.provider, sampleStorage.category.id, sampleStorage.id),
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
                sampleCompute.copy(id = "qweasd"),
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
                sampleStorage.copy(id = "notacomputeproduct", category = sampleCompute.category),
                serviceClient
            ),
            "fails because we changed area"
        ) { it.statusCode == HttpStatusCode.BadRequest }

        assertThatInstance(
            Products.updateProduct.call(
                sampleStorage.copy(id = sampleCompute.id, category = sampleCompute.category),
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
}

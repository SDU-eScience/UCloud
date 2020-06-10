package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.accounting.api.AccountingServiceDescription
import dk.sdu.cloud.service.test.TestDB
import dk.sdu.cloud.accounting.api.FindProductRequest
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.accounting.services.ProductService
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.toActor
import io.ktor.http.HttpStatusCode
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.*

val computeProduct = Product.Compute(
    "u1-standard-1",
    1000L,
    ProductCategoryId("standard", UCLOUD_PROVIDER),
    cpu = 1,
    memoryInGigs = 4,
    gpu = 0
)

val storageProduct = Product.Storage(
    "u1-cephfs",
    1000L,
    ProductCategoryId("cephfs", UCLOUD_PROVIDER)
)

class ProductServiceTest {
    @AfterTest
    fun afterTest() {
        runBlocking {
            db.withSession { session ->
                session.sendQuery("delete from products where true")
                session.sendQuery("delete from product_categories where true")
            }
        }
    }

    @Test
    fun `basic create and find`(): Unit = runBlocking {
        run {
            products.create(db, Actor.System, computeProduct)
            val product = products.find(
                db,
                Actor.System,
                FindProductRequest(computeProduct.category.provider, computeProduct.category.id, computeProduct.id)
            )
            assertEquals(computeProduct, product)
        }

        run {
            products.create(db, Actor.System, storageProduct)
            val product = products.find(
                db,
                Actor.System,
                FindProductRequest(storageProduct.category.provider, storageProduct.category.id, storageProduct.id)
            )
            assertEquals(storageProduct, product)
        }
        return@runBlocking
    }

    @Test
    fun `create and list`(): Unit = runBlocking {
        db.withSession { session ->
            repeat(50) { i ->
                products.create(
                    session,
                    Actor.System,
                    computeProduct.copy(id = "u1-standard-$i", cpu = i, priority = i)
                )
            }
        }

        val page50 = products.list(db, Actor.System, computeProduct.category.provider, NormalizedPaginationRequest(50, 0))
        assertEquals(1, page50.pagesInTotal)
        assertEquals(50, page50.itemsInTotal)
        repeat(50) { i ->
            assertThatPropertyEquals(page50.items[i], { it.id }, "u1-standard-$i", "Expected $i to be u1-standard-$i")
        }

        val page10 = products.list(db, Actor.System, computeProduct.category.provider, NormalizedPaginationRequest(10, 0))
        assertEquals(5, page10.pagesInTotal)
        assertEquals(50, page10.itemsInTotal)
        repeat(10) { i ->
            assertThatPropertyEquals(page10.items[i], { it.id }, "u1-standard-$i", "Expected $i to be u1-standard-$i")
        }
        return@runBlocking
    }

    @Test
    fun `test permission denied`(): Unit = runBlocking {
        try {
            products.create(db, TestUsers.user.toActor(), computeProduct)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.Forbidden, ex.httpStatusCode)
        }
        return@runBlocking
    }

    @Test
    fun `test changing category fails`(): Unit = runBlocking {
        products.create(db, Actor.System, computeProduct)
        try {
            products.create(db, Actor.System, storageProduct.copy(category = computeProduct.category))
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.BadRequest, ex.httpStatusCode)
        }
        return@runBlocking
    }

    @Test
    fun `test duplicate fails`(): Unit = runBlocking {
        products.create(db, Actor.System, computeProduct)
        try {
            products.create(db, Actor.System, computeProduct)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.Conflict, ex.httpStatusCode)
        }
        return@runBlocking
    }

    @Test
    fun `test updating`(): Unit = runBlocking {
        products.create(db, Actor.System, computeProduct)
        val newProduct = computeProduct.copy(cpu = 2)
        products.update(db, Actor.System, newProduct)
        val retrievedProduct = products.find(
            db,
            Actor.System,
            FindProductRequest(computeProduct.category.provider, computeProduct.category.id, computeProduct.id)
        )
        assertEquals(newProduct, retrievedProduct)
        return@runBlocking
    }

    @Test
    fun `test updating to different category`(): Unit = runBlocking {
        products.create(db, Actor.System, computeProduct)
        try {
            products.update(
                db,
                Actor.System,
                storageProduct.copy(id = computeProduct.id, category = computeProduct.category)
            )
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.BadRequest, ex.httpStatusCode)
        }
        return@runBlocking
    }

    @Test
    fun `test updating non-existing product`(): Unit = runBlocking {
        // Test when category doesn't exist
        try {
            products.update(db, Actor.System, computeProduct)
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.NotFound, ex.httpStatusCode)
        }

        // Test when category exists
        products.create(db, Actor.System, computeProduct)
        try {
            products.update(db, Actor.System, computeProduct.copy(id = "u1-standard-2"))
            assert(false)
        } catch (ex: RPCException) {
            assertEquals(HttpStatusCode.NotFound, ex.httpStatusCode)
        }
        return@runBlocking
    }

    companion object {
        private lateinit var db: AsyncDBSessionFactory
        private lateinit var embeddedDatabase: EmbeddedPostgres
        private lateinit var products: ProductService

        @BeforeClass
        @JvmStatic
        fun before() {
            val (db, embDB) = TestDB.from(AccountingServiceDescription)
            this.db = db
            this.embeddedDatabase = embDB
            this.products = ProductService()
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            runBlocking {
                db.close()
            }
            embeddedDatabase.close()
        }
    }
}

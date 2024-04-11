package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.ReadWriterMutex
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong

interface IProductCache {
    suspend fun referenceToProduct(ref: ProductReferenceV2): ProductV2? {
        val id = referenceToProductId(ref) ?: return null
        return productIdToProduct(id)
    }

    suspend fun referenceToProductId(ref: ProductReferenceV2): Int?
    suspend fun productIdToReference(id: Int): ProductReferenceV2?
    suspend fun productIdToProduct(id: Int): ProductV2?
    suspend fun productNameToProductIds(name: String): List<Int>?
    suspend fun productCategoryToProductIds(category: String): List<Int>?
    suspend fun productProviderToProductIds(provider: String): List<Int>?
    suspend fun products(): List<ProductV2>

    suspend fun productCategory(id: ProductCategoryIdV2): ProductCategory? {
        return products().find { it.category.toId() == id }?.category
    }
}

fun List<ProductV2>.findAllFreeProducts(): List<ProductV2> {
    return filter { it.category.freeToUse }
}

class ProductCache(private val db: DBContext) : IProductCache {
    private val mutex = ReadWriterMutex()
    private val referenceToProductId = HashMap<ProductReferenceV2, Int>()
    private val productIdToReference = HashMap<Int, ProductReferenceV2>()
    private val productInformation = HashMap<Int, ProductV2>()
    private val productNameToProductIds = HashMap<String, ArrayList<Int>>()
    private val productCategoryToProductIds = HashMap<String, ArrayList<Int>>()
    private val productProviderToProductIds = HashMap<String, ArrayList<Int>>()
    private val nextFill = AtomicLong(0L)

    private suspend fun fillCache(force: Boolean = false) {
        if (!force && Time.now() < nextFill.get()) return
        mutex.withWriter {
            if (!force && Time.now() < nextFill.get()) return

            referenceToProductId.clear()
            productIdToReference.clear()
            productInformation.clear()
            productNameToProductIds.clear()
            productCategoryToProductIds.clear()
            productProviderToProductIds.clear()

            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        declare product_load cursor for
                        select accounting.product_to_json(p, pc, au, 0), p.id
                        from
                            accounting.products p join
                            accounting.product_categories pc on p.category = pc.id join
                            accounting.accounting_units au on pc.accounting_unit = au.id
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 100 from product_load").rows
                    if (rows.isEmpty()) break

                    rows.forEach { row ->
                        val product = defaultMapper.decodeFromString(ProductV2.serializer(), row.getString(0)!!)
                        val id = row.getLong(1)!!.toInt()

                        val reference = ProductReferenceV2(product.name, product.category.name, product.category.provider)
                        referenceToProductId[reference] = id
                        productIdToReference[id] = reference
                        productInformation[id] = product

                        productNameToProductIds.getOrPut(product.name) { ArrayList() }.add(id)
                        productCategoryToProductIds.getOrPut(product.category.name) { ArrayList() }.add(id)
                        productProviderToProductIds.getOrPut(product.category.provider) { ArrayList() }.add(id)
                    }
                }
            }

            nextFill.set(Time.now() + 60_000 * 5)
        }
    }

    private suspend inline fun <R> tryOrRetry(block: () -> R?): R? {
        println("Trying")
        val result = block()
        if (result != null) return result
        println("Retrying!")
        fillCache(force = true)
        return block()
    }

    override suspend fun referenceToProductId(ref: ProductReferenceV2): Int? {
        return tryOrRetry {
            fillCache()
            mutex.withReader {
                referenceToProductId[ref]
            }
        }
    }

    override suspend fun productIdToReference(id: Int): ProductReferenceV2? {
        return tryOrRetry {
            fillCache()
            mutex.withReader {
                productIdToReference[id]
            }
        }
    }

    override suspend fun productIdToProduct(id: Int): ProductV2? {
        return tryOrRetry {
            fillCache()
            mutex.withReader {
                productInformation[id]
            }
        }
    }

    override suspend fun productNameToProductIds(name: String): List<Int>? {
        return tryOrRetry {
            fillCache()
            mutex.withReader {
                productNameToProductIds[name]
            }
        }
    }

    override suspend fun productCategoryToProductIds(category: String): List<Int>? {
        return tryOrRetry {
            fillCache()
            mutex.withReader {
                productCategoryToProductIds[category]
            }
        }
    }

    override suspend fun productProviderToProductIds(provider: String): List<Int>? {
        return tryOrRetry {
            fillCache()
            return mutex.withReader {
                productProviderToProductIds[provider]
            }
        }
    }

    override suspend fun products(): List<ProductV2> {
        fillCache()
        return mutex.withReader {
            ArrayList(productInformation.values)
        }
    }

    override suspend fun productCategory(id: ProductCategoryIdV2): ProductCategory? {
        println("In here! $id")
        return tryOrRetry {
            val message = products()
            println(message)
            message.find { it.category.toId() == id }?.category
        }
    }
}


class FakeProductCache : IProductCache {
    fun Product.toReference(): ProductReference {
        return ProductReference(name, category.name, category.provider)
    }

    private fun ProductV2.toReference(): ProductReferenceV2? {
        return ProductReferenceV2(name, category.name, category.provider)
    }


    @Volatile
    private var products = listOf<ProductV2>(
        ProductV2.Compute(
            name = "-",
            price = 1L,
            category = ProductCategory("-", "-", ProductType.COMPUTE, AccountingUnit("DKK", "DKK", true, false), AccountingFrequency.PERIODIC_MINUTE),
            description = "-"
        )
    )
    private val insertMutex = Mutex()

    suspend fun insert(product: ProductV2): Int {
        insertMutex.withLock {
            val newList = products + product
            products = newList
            return newList.size - 1
        }
    }

    suspend fun insert(name: String, category: String, provider: String): Int {
        return insert(
            ProductV2.Compute(
                name,
                1L,
                ProductCategory(
                    name,
                    provider,
                    ProductType.COMPUTE,
                    AccountingUnit(
                        name = "DKK",
                        namePlural = "DKK",
                        floatingPoint = true,
                        displayFrequencySuffix = false
                    ),
                    AccountingFrequency.PERIODIC_MINUTE
                ),
                description = "Test"
            )
        )
    }

    override suspend fun referenceToProductId(ref: ProductReference): Int? {
        val list = products
        for (i in list.indices) {
            val product = list[i]
            if (product.name == ref.id && product.category.name == ref.category && product.category.provider == ref.provider) {
                return i
            }
        }
        return -1
    }

    override suspend fun productIdToReference(id: Int): ProductReferenceV2? {
        val list = products
        if (id < 0 || id >= list.size) return null
        return list[id].toReference()
    }

    override suspend fun productIdToProduct(id: Int): ProductV2? {
        return products.getOrNull(id)
    }

    override suspend fun productNameToProductIds(name: String): List<Int>? {
        val result = ArrayList<Int>()
        val list = products
        for (i in list.indices) {
            if (list[i].name == name) result.add(i)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun productCategoryToProductIds(category: String): List<Int>? {
        val result = ArrayList<Int>()
        val list = products
        for (i in list.indices) {
            if (list[i].category.name == category) result.add(i)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun productProviderToProductIds(provider: String): List<Int>? {
        val result = ArrayList<Int>()
        val list = products
        for (i in list.indices) {
            if (list[i].category.provider == provider) result.add(i)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun products(): List<ProductV2> {
        return products
    }
}
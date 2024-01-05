package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.ProductReferenceV2
import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.ReadWriterMutex
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
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

    private suspend fun fillCache() {
        if (Time.now() < nextFill.get()) return
        mutex.withWriter {
            if (Time.now() < nextFill.get()) return

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

    override suspend fun referenceToProductId(ref: ProductReferenceV2): Int? {
        fillCache()
        return mutex.withReader {
            referenceToProductId[ref]
        }
    }

    override suspend fun productIdToReference(id: Int): ProductReferenceV2? {
        fillCache()
        return mutex.withReader {
            productIdToReference[id]
        }
    }

    override suspend fun productIdToProduct(id: Int): ProductV2? {
        fillCache()
        return mutex.withReader {
            productInformation[id]
        }
    }

    override suspend fun productNameToProductIds(name: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productNameToProductIds[name]
        }
    }

    override suspend fun productCategoryToProductIds(category: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productCategoryToProductIds[category]
        }
    }

    override suspend fun productProviderToProductIds(provider: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productProviderToProductIds[provider]
        }
    }

    override suspend fun products(): List<ProductV2> {
        fillCache()
        return mutex.withReader {
            ArrayList(productInformation.values)
        }
    }
}

package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong

class ProductCache(private val db: DBContext) {
    private val mutex = ReadWriterMutex()
    private val referenceToProductId = HashMap<ProductReference, Int>()
    private val productIdToReference = HashMap<Int, ProductReference>()
    private val productInformation = HashMap<Int, Product>()
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
                        select accounting.product_to_json(p, pc, 0), p.id
                        from
                            accounting.products p join
                            accounting.product_categories pc on
                                p.category = pc.id
                    """
                )

                while (true) {
                    val rows = session.sendPreparedStatement({}, "fetch forward 100 from product_load").rows
                    if (rows.isEmpty()) break

                    rows.forEach { row ->
                        val product = defaultMapper.decodeFromString(Product.serializer(), row.getString(0)!!)
                        val id = row.getLong(1)!!.toInt()

                        val reference = ProductReference(product.name, product.category.name, product.category.provider)
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

    suspend fun referenceToProductId(ref: ProductReference): Int? {
        fillCache()
        return mutex.withReader {
            referenceToProductId[ref]
        }
    }

    suspend fun productIdToReference(id: Int): ProductReference? {
        fillCache()
        return mutex.withReader {
            productIdToReference[id]
        }
    }

    suspend fun productIdToProduct(id: Int): Product? {
        fillCache()
        return mutex.withReader {
            productInformation[id]
        }
    }

    suspend fun productNameToProductIds(name: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productNameToProductIds[name]
        }
    }

    suspend fun productCategoryToProductIds(category: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productCategoryToProductIds[category]
        }
    }

    suspend fun productProviderToProductIds(provider: String): List<Int>? {
        fillCache()
        return mutex.withReader {
            productProviderToProductIds[provider]
        }
    }

    suspend fun products(): List<Product> {
        fillCache()
        return mutex.withReader {
            ArrayList(productInformation.values)
        }
    }
}

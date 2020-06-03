package dk.sdu.cloud.accounting.compute.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.ktor.http.HttpMethod

enum class ProductArea {
    STORAGE,
    COMPUTE
}

data class ProductCategory(
    val id: ProductCategoryId,
    val area: ProductArea
)

data class ProductCategoryId(
    val id: String,
    val provider: String
)

typealias CreateProductRequest = Product
typealias CreateProductResponse = Unit

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ProductAvailability.Available::class, name = "available"),
    JsonSubTypes.Type(value = ProductAvailability.Unavailable::class, name = "unavailable")
)
sealed class ProductAvailability {
    object Available : ProductAvailability()
    class Unavailable(val reason: String) : ProductAvailability()
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Product.Storage::class, name = "storage"),
    JsonSubTypes.Type(value = Product.Compute::class, name = "compute")
)
sealed class Product {
    abstract val category: ProductCategoryId
    abstract val pricePerUnit: Long
    abstract val id: String
    abstract val description: String
    abstract val availability: ProductAvailability

    data class Storage(
        override val id: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val availability: ProductAvailability = ProductAvailability.Available
    ) : Product() {
        init {
            require(pricePerUnit >= 0)
            require(id.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }

    data class Compute(
        override val id: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val availability: ProductAvailability = ProductAvailability.Available,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null
    ) : Product() {
        init {
            require(pricePerUnit >= 0)
            require(id.isNotBlank())
            require(description.count { it == '\n' } == 0)

            if (gpu != null) require(gpu >= 0) { "gpu is negative ($this)" }
            if (cpu != null) require(cpu >= 0) { "cpu is negative ($this)" }
            if (memoryInGigs != null) require(memoryInGigs >= 0) { "memoryInGigs is negative ($this)" }
        }
    }
}

data class SetAvailabilityRequest(val productId: String, val availability: ProductAvailability)
typealias SetAvailabilityResponse = Unit

data class FindProductRequest(val ProductCategoryId

object Products : CallDescriptionContainer("products") {
    const val baseContext = "/api/products"

    /**
     * Creates a new [Product]
     *
     * Note that only the provider themselves are allowed to push a new [Product] to the database. A matching
     * [ProductCategory] is automatically created when the first [Product] in that category is created.
     */
    val createProduct = call<CreateProductRequest, CreateProductResponse, CommonErrorMessage>("createProduct") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val setAvailability = call<SetAvailabilityRequest, SetAvailabilityResponse, CommonErrorMessage>("setAvailability") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"availability"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val findProduct = call<FindProductRequest, FindProductResponse, CommonErrorMessage>("findProduct") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(FindProductRequest::id)
                +boundTo(FindProductRequest::provider)
            }
        }
    }
}
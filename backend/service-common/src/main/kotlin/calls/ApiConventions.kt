package dk.sdu.cloud.calls

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import io.ktor.http.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object UCloudApi {
    const val RETRIEVE = "retrieve"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val VERIFY = "verify"
}

inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpCreate(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Post
        path { using(baseContext) }
        body { bindEntireRequestFromBody() }
    }
}

inline fun <reified R : Any> CallDescription<R, *, *>.httpBrowse(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ
        this.roles = roles
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
            +UCloudApi.BROWSE
        }

        if (R::class != Unit::class) {
            params {
                R::class.memberProperties.forEach { param ->
                    if (R::class.primaryConstructor?.parameters?.any { it.name == param.name } == true) {
                        +boundTo(param)
                    }
                }
            }
        }
    }
}

inline fun <reified R : Any> CallDescription<R, *, *>.httpRetrieve(
    baseContext: String,
    subResource: String? = null,
    roles: Set<Role> = Roles.END_USER
) {
    auth {
        access = AccessRight.READ
        this.roles = roles
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"${UCloudApi.RETRIEVE}${subResource?.capitalize() ?: ""}"
        }

        if (R::class != Unit::class) {
            params {
                R::class.memberProperties.forEach { param ->
                    if (R::class.primaryConstructor?.parameters?.any { it.name == param.name } == true) {
                        +boundTo(param)
                    }
                }
            }
        }
    }
}

inline fun <reified R : Any> CallDescription<R, *, *>.httpSearch(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER
) {
    auth {
        access = AccessRight.READ
        this.roles = roles
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +UCloudApi.SEARCH
        }

        body { bindEntireRequestFromBody() }
    }
}

inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpUpdate(
    baseContext: String,
    operation: String,
    roles: Set<Role> = Roles.END_USER
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +operation
        }

        body { bindEntireRequestFromBody() }
    }
}


inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpDelete(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Delete

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }
}

inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpVerify(
    baseContext: String,
    informationToVerify: String? = null,
    roles: Set<Role> = Roles.PRIVILEGED
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"${UCloudApi.VERIFY}${informationToVerify?.capitalize() ?: ""}"
        }

        body { bindEntireRequestFromBody() }
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = BulkRequest.Single::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = BulkRequest.Bulk::class, name = "bulk"),
)
sealed class BulkRequest<T> {
    abstract val items: List<T>

    data class Single<T>(@JsonUnwrapped val item: T) : BulkRequest<T>() {
        @JsonIgnore
        override val items = listOf(item)
    }

    data class Bulk<T>(override val items: List<T>) : BulkRequest<T>()
}

fun <T> bulkRequestOf(vararg items: T): BulkRequest<T> {
    if (items.isEmpty()) error("No items provided")
    return if (items.size == 1) BulkRequest.Single(items.single())
    else BulkRequest.Bulk(listOf(*items))
}

fun <T> bulkRequestOf(items: Collection<T>): BulkRequest<T> {
    if (items.isEmpty()) error("No items provided")
    return if (items.size == 1) BulkRequest.Single(items.single())
    else BulkRequest.Bulk(items.toList())
}
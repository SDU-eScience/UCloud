package dk.sdu.cloud.calls

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Roles
import io.ktor.http.*
import kotlin.reflect.full.memberProperties

object UCloudApi {
    const val RETRIEVE = "retrieve"
    const val BROWSE = "browse"
    const val SEARCH = "search"
}

inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpCreate(baseContext: String) {
    auth {
        access = AccessRight.READ_WRITE
    }

    http {
        method = HttpMethod.Post
        path { using(baseContext) }
        body { bindEntireRequestFromBody() }
    }
}

inline fun <reified R : Any> CallDescription<R, *, *>.httpBrowse(baseContext: String) {
    auth {
        access = AccessRight.READ
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
            +UCloudApi.BROWSE
        }

        params {
            R::class.memberProperties.forEach {
                +boundTo(it)
            }
        }
    }
}

inline fun <reified R : Any> CallDescription<R, *, *>.httpRetrieve(baseContext: String, subResource: String? = null) {
    auth {
        access = AccessRight.READ
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"${UCloudApi.RETRIEVE}${subResource?.capitalize() ?: ""}"
        }

        params {
            R::class.memberProperties.forEach {
                +boundTo(it)
            }
        }
    }
}

inline fun <reified R : Any> CallDescription<R, *, *>.httpSearch(baseContext: String) {
    auth {
        access = AccessRight.READ
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
) {
    auth {
        access = AccessRight.READ_WRITE
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
) {
    auth {
        access = AccessRight.READ_WRITE
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
    informationToVerify: String = "",
) {
    auth {
        access = AccessRight.READ_WRITE
        roles = Roles.PRIVILEGED
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"verify${informationToVerify.capitalize()}"
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
        @JsonIgnore override val items = listOf(item)
    }
    data class Bulk<T>(override val items: List<T>) : BulkRequest<T>()
}

fun <T> bulkRequestOf(vararg items: T): BulkRequest<T> {
    if (items.isEmpty()) error("No items provided")
    return if (items.size == 1) BulkRequest.Single(items.single())
    else BulkRequest.Bulk(listOf(*items))
}
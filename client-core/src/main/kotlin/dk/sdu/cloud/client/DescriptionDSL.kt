package dk.sdu.cloud.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod
import kotlin.reflect.KProperty1

@DslMarker
annotation class RESTCallDSL

@RESTCallDSL
class RESTCallDescriptionBuilder<Request : Any, Success : Any, Error : Any, AuditEntry : Any>(
    private val requestType: TypeReference<Request>,
    private val responseTypeSuccess: TypeReference<Success>,
    private val responseTypeFailure: TypeReference<Error>,
    private val normalizedRequestTypeForAudit: TypeReference<AuditEntry>,

    private val deserializerSuccess: ObjectReader,
    private val deserializerError: ObjectReader
) {
    var method: HttpMethod = HttpMethod.Get
    var name: String? = null
    @Deprecated(message = "Use name instead", replaceWith = ReplaceWith("name"))
    var prettyName: String?
        get() = name
        set(value) {
            name = value
        }

    internal var path: RESTPath<Request>? = null
    internal var body: RESTBody<Request, *>? = null
    internal var params: RESTParams<Request>? = null
    internal var auth: RESTAuth? = null

    fun path(body: RESTCallPathBuilder<Request>.() -> Unit) {
        if (path != null) throw RESTDSLException("Cannot supply two path blocks!")
        path = RESTCallPathBuilder(this).also { it.body() }.build()
    }

    fun body(builderBody: RESTCallBodyBuilder<Request>.() -> Unit) {
        if (body != null) throw RESTDSLException("Cannot supply two body blocks!")
        body = RESTCallBodyBuilder(this).also(builderBody).body ?: throw RESTDSLException("Missing entries in body{}")
    }

    fun params(builderBody: RESTCallQueryParamsBuilder<Request>.() -> Unit) {
        if (params != null) throw RESTDSLException("Cannot supply two param blocks!")
        params = RESTCallQueryParamsBuilder<Request>().also(builderBody).build()
    }

    fun auth(builderBody: RESTAuthBuilder.() -> Unit) {
        if (auth != null) throw RESTDSLException("Cannot supply two auth blocks!")
        auth = RESTAuthBuilder().also(builderBody).build()
    }

    fun build(
        namespace: String,
        additionalConfiguration: (HttpRequestBuilder.(Request) -> Unit)?
    ): RESTCallDescription<Request, Success, Error, AuditEntry> {
        val path = path ?: throw RESTDSLException("Missing path { ... }!")
        val name = name ?: throw RESTDSLException("Missing name = ...!")
        val fullName = "$namespace.$name"
        val auth = auth ?: throw RESTDSLException("Missing auth { ... }!")

        return RESTCallDescription(
            method,
            path,
            body,
            params,
            auth,
            requestType,
            responseTypeSuccess,
            responseTypeFailure,
            normalizedRequestTypeForAudit,
            deserializerSuccess,
            deserializerError,
            namespace,
            fullName,
            additionalConfiguration
        )
    }
}

@RESTCallDSL
class RESTAuthBuilder {
    var roles: Set<Role> = Roles.END_USER
    var access: AccessRight? = null

    fun build(): RESTAuth {
        val access = access ?: throw RESTDSLException("""
                Missing auth.access!

                This property describes if the operation is read or read/write.
                In cases where it can be both it should be set to read. This property is
                enforced through authorization scopes.

                Each authentication token contains a number of scopes. A scope describes what a
                user can do with a certain resource, and only reads are allowed or if read/writes
                is possible.

                These scopes are used for third party applications (OAuth) and internally for token extensions.

                Examples:

                callDescription<..., ..., ...> {
                    name = "uploadFile"

                    auth {
                        // Uploading a file will write new data into the system
                        access = AccessRight.READ_WRITE
                    }
                }

                callDescription<..., ..., ...> {
                    name = "listDirectory"

                    auth {
                        // Listing files in a directory will not modify any data in the system
                        access = AccessRight.READ
                    }
                }
            """.trimIndent())

        return RESTAuth(roles, access)
    }
}

@RESTCallDSL
class RESTCallQueryParamsBuilder<R : Any> {
    private val params = ArrayList<RESTQueryParameter<R>>()

    private fun addParam(param: RESTQueryParameter<R>) {
        params.add(param)
    }

    operator fun RESTQueryParameter<R>.unaryPlus() {
        addParam(this)
    }

    fun boundTo(property: KProperty1<R, *>): RESTQueryParameter<R> {
        return RESTQueryParameter.Property(property)
    }

    fun build(): RESTParams<R> = RESTParams(params)
}

@RESTCallDSL
class RESTCallPathBuilder<R : Any>(private val parent: RESTCallDescriptionBuilder<R, *, *, *>) {
    private var basePath = ""
    private val segments = ArrayList<RESTPathSegment<R>>()

    private fun addSegment(segment: RESTPathSegment<R>) {
        if (segment is RESTPathSegment.Property<*, *>) {
            val isLegal = parent.body !is RESTBody.BoundToEntireRequest<*>
            if (!isLegal) {
                throw RESTDSLException(
                    """
                    Cannot bind a property to the both, the entire request has already been to the body.

                    In your code you have already bound the entire request object to the body, i.e.:

                    body {
                        bindEntireRequestFromBody()
                    }

                    If you intend to use bound properties from the path _and_ the body, then the body must be bound
                    using bindToSubProperty. For example:

                    data class Foo(val a: Int, val b: Int)
                    data class MyRequest(val pathBound: String, val bodyBound: Foo)

                    callDescription {
                        path {
                            +boundTo(MyRequest::pathBound)
                        }

                        body {
                            bindToSubProperty(MyRequest::bodyBound)
                        }
                    }
                    """.trimIndent()
                )
            }

            val alreadyExists = segments.filterIsInstance<RESTPathSegment.Property<*, *>>().find {
                it.property == segment.property
            } != null
            if (alreadyExists) {
                throw RESTDSLException(
                    """
                    Cannot bind the same property twice!

                    Somewhere in the code you have already bound this property, i.e.:

                    path {
                        +boundTo(MyRequest::${segment.property.name})
                        // ...
                        +boundTo(MyRequest::${segment.property.name}) // <-- Second call not OK
                    }
                    """.trimIndent()
                )
            }
        }

        segments.add(segment)
    }

    operator fun String.unaryPlus() {
        addSegment(RESTPathSegment.Simple(this))
    }

    operator fun RESTPathSegment<R>.unaryPlus() {
        addSegment(this)
    }

    fun using(path: String) {
        if (basePath.isNotEmpty()) throw RESTDSLException("Cannot call using(path) twice!")
        basePath = path
    }

    fun <T> boundTo(property: KProperty1<R, T>): RESTPathSegment.Property<R, T> = RESTPathSegment.Property(property)
    fun remaining(): RESTPathSegment.Remaining<R> = RESTPathSegment.Remaining()

    internal fun build(): RESTPath<R> {
        return RESTPath(basePath, segments)
    }
}

@RESTCallDSL
class RESTCallBodyBuilder<R : Any>(private val parent: RESTCallDescriptionBuilder<R, *, *, *>) {
    internal var body: RESTBody<R, *>? = null

    fun bindEntireRequestFromBody(ref: TypeReference<R>) {
        checkNotBound()

        val isLegal = parent.path?.segments?.find { it is RESTPathSegment.Property<*, *> } == null
        if (!isLegal) {
            throw RESTDSLException(
                """
                Cannot bind the entire request from body, request body already contains bindings.

                You have already supplied at least one path segment which is bound to a property, e.g.:

                path {
                    +boundTo(MyRequest::pathBound)
                }

                If you intend to use bound properties from the path _and_ the body, then the body must be bound using
                bindToSubProperty. For example:

                data class Foo(val a: Int, val b: Int)
                data class MyRequest(val pathBound: String, val bodyBound: Foo)

                callDescription {
                    path {
                        +boundTo(MyRequest::pathBound)
                    }

                    body {
                        bindToSubProperty(MyRequest::bodyBound)
                    }
                }
                """.trimIndent()
            )
        }

        body = RESTBody.BoundToEntireRequest(ref)
    }

    fun <F : Any> bindToSubProperty(property: KProperty1<R, F>, ref: TypeReference<F>) {
        checkNotBound()

        body = RESTBody.BoundToSubProperty(property, ref)
    }

    private fun checkNotBound() {
        if (body != null) {
            throw RESTDSLException("Cannot call body { bind*(...) } twice!")
        }
    }
}

class RESTDSLException(message: String) : Exception(message)

inline fun <reified R : Any> RESTCallBodyBuilder<R>.bindEntireRequestFromBody() {
    bindEntireRequestFromBody(jacksonTypeRef())
}

inline fun <R : Any, reified F : Any> RESTCallBodyBuilder<R>.bindToSubProperty(property: KProperty1<R, F>) {
    bindToSubProperty(property, jacksonTypeRef())
}

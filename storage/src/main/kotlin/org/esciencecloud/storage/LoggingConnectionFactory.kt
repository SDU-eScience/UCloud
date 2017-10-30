package org.esciencecloud.storage

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import com.fasterxml.jackson.module.kotlin.*
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.jvm.kotlinFunction

// This is a component for creating logging associated with each public request.
// This is most likely not needed, but what part of the initial design. Keeping it here, in case we need it.

// TODO FIXME Needs to not throw exceptions when arguments cannot be serialized (e.g. input streams)
private const val SERVICE = "service"
abstract class LoggingStorageConnection(private val delegate: StorageConnection) : StorageConnection {
    // Metadata added to all logging entries. Should identify why this request is occurring
    private val loggingMeta: Map<String, Any?> by lazy { mapOf(
            "user" to connectedUser.name
    ) }

    override val paths: PathOperations = delegate.paths

    override val connectedUser: User by lazy { delegate.connectedUser }

    override val files: FileOperations by lazy {
        LoggingProxy.wrap(delegate.files, loggingMeta + (SERVICE to "files"))
    }

    override val metadata: MetadataOperations by lazy {
        LoggingProxy.wrap(delegate.metadata, loggingMeta + (SERVICE to "metadata"))
    }

    override val fileQuery: FileQueryOperations by lazy {
        LoggingProxy.wrap(delegate.fileQuery, loggingMeta + (SERVICE to "fileQuery"))
    }

    override val users: UserOperations by lazy {
        LoggingProxy.wrap(delegate.users, loggingMeta + (SERVICE to "users"))
    }

    override val groups: GroupOperations by lazy {
        LoggingProxy.wrap(delegate.groups, loggingMeta + (SERVICE to "groups"))
    }

    override val accessControl: AccessControlOperations by lazy {
        LoggingProxy.wrap(delegate.accessControl, loggingMeta + (SERVICE to "accessControl"))
    }

    override fun close() {
        delegate.close()
    }
}

data class Perf(val name: String, val args: Map<String, Any?>, val time: Long, val meta: Map<String, Any?>,
                val throwable: Throwable?)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class SkipAuditing

class LoggingProxy(private val delegate: Any, private val metadata: Map<String, Any?> = emptyMap()) :
        InvocationHandler {
    private val mapper = jacksonObjectMapper()

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        val isLogging = method.declaringClass != Any::class.java &&
                method.annotations.none { it.annotationClass == SkipAuditing::class }
        val kotlinFunction = if (isLogging) method.kotlinFunction else null
        if (!isLogging || kotlinFunction == null) {
            try {
                return if (args == null) {
                    method.invoke(delegate)
                } else {
                    method.invoke(delegate, *args)
                }
            } catch (ex: InvocationTargetException) {
                throw ex.cause ?: ex
            }
        } else {

            val validParameters = kotlinFunction.parameters.filter {
                it.name != null && it.annotations.none { it.annotationClass == SkipAuditing::class }
            }
            val arguments = validParameters.associate { it.name!! to args?.getOrNull(it.index - 1) }

            val start = System.currentTimeMillis()
            var caughtThrowable: Throwable? = null
            try {
                return if (args == null) {
                    method.invoke(delegate)
                } else {
                    method.invoke(delegate, *args)
                }
            } catch (throwable: InvocationTargetException) {
                //method.invoke wraps the exception into an InvocationTargetException. We need to unwrap and rethrow
                val cause = throwable.cause ?: throwable
                caughtThrowable = cause
                throw caughtThrowable
            } finally {
                val end = System.currentTimeMillis()
                println(mapper.writeValueAsString(Perf(method.name, arguments, end - start, metadata,
                        caughtThrowable)))
            }
        }
    }

    companion object {
        inline fun <reified T> wrap(delegate: T, metadata: Map<String, Any?> = emptyMap()): T {
            return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java),
                    LoggingProxy(delegate!!, metadata)) as T
        }
    }
}

package dk.sdu.cloud.storage.services.ext.irods

import dk.sdu.cloud.storage.services.ext.StorageException
import org.irods.jargon.core.exception.*
import java.io.FileNotFoundException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

inline fun <T> remapException(call: () -> T): T {
    try {
        return call()
    } catch (exception: Exception) {
        throw remapException(exception)
    }
}

fun <T> createIRodsInvocationHandler(delegate: T) = InvocationHandler { _, method, args ->
    remapException {
        method.invoke(delegate, *args)
    }
}

inline fun <reified T> createIRodsProxy(delegate: T): T {
    return Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        createIRodsInvocationHandler(delegate)
    ) as T
}

fun remapException(exception: Throwable): Exception {
    when (exception) {
        is FileNotFoundException, is org.irods.jargon.core.exception.FileNotFoundException -> {
            return StorageException.NotFound("object", "Unknown", exception.message ?: "Unknown")
        }
        is InvalidGroupException -> {
            return StorageException.NotFound("usergroup", "Unknown", exception.message ?: "Unknown")
        }
        is DuplicateDataException -> {
            return StorageException.Duplicate("Cannot create new entry - Entry already exists. Cause: ${exception.message}")
        }
        is CatNoAccessException -> {
            return StorageException.BadPermissions("Not allowed. Cause: ${exception.message}")
        }
        is DataNotFoundException -> {
            return StorageException.NotFound("Unknown", "Unknown", exception.message ?: "Unknown")
        }

        // Needs to be just before the else branch since this is the super type of all Jargon exceptions
        is JargonException -> {
            val cause = exception.cause as? Exception
            return if (cause != null) {
                remapException(cause)
            } else {
                RuntimeException("Caught unexpected exception", exception)
            }
        }

        else -> {
            return RuntimeException("Exception in iRODS. Cause is unknown.", exception)
        }
    }
}

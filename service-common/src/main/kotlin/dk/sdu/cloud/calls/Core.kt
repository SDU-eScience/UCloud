package dk.sdu.cloud.calls

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import java.util.*

data class AttributeKey<V : Any>(val key: String)

class AttributeContainer {
    private val internalContainer = HashMap<AttributeKey<*>, Any>()

    operator fun <V : Any> set(key: AttributeKey<V>, value: V) {
        internalContainer[key] = value
    }

    fun <V : Any> remove(key: AttributeKey<V>) {
        internalContainer.remove(key)
    }

    operator fun <V : Any> get(key: AttributeKey<V>): V {
        synchronized(this) {
            val result = internalContainer[key] ?: throw IllegalArgumentException("No such key!")
            @Suppress("UNCHECKED_CAST")
            return result as V
        }
    }

    fun <V : Any> getOrNull(key: AttributeKey<V>): V? {
        return try {
            get(key)
        } catch (ex: IllegalArgumentException) {
            return null
        }
    }
}

class CallDescription<Request : Any, Success : Any, Error : Any> internal constructor(
    val name: String,
    val namespace: String,
    val attributes: AttributeContainer,
    val requestType: TypeReference<Request>,
    val successType: TypeReference<Success>,
    val errorType: TypeReference<Error>
) {
    val fullName: String get() = "$namespace.$name"

    override fun toString(): String = "CallDescription($fullName)"
}

abstract class CallDescriptionContainer(val namespace: String) {
    private val _callContainer = ArrayList<CallDescription<*, *, *>>()
    val callContainer: List<CallDescription<*, *, *>>
        get() = Collections.unmodifiableList(_callContainer)

    fun <Request : Any, Success : Any, Error : Any> call(
        name: String,
        handler: (CallDescription<Request, Success, Error>.() -> Unit),
        requestType: TypeReference<Request>,
        successType: TypeReference<Success>,
        errorType: TypeReference<Error>
    ): CallDescription<Request, Success, Error> {
        val callDescription = CallDescription(name, namespace, AttributeContainer(), requestType, successType, errorType)
        callDescription.handler()
        _callContainer.add(callDescription)
        onBuildHandlers.forEach { it(callDescription) }
        return callDescription
    }

    companion object {
        private val onBuildHandlers = ArrayList<OnCallDescriptionBuildHandler>()

        fun onBuild(handler: OnCallDescriptionBuildHandler) {
            synchronized(this) {
                onBuildHandlers.add(handler)
            }
        }
    }
}

inline fun <reified Request : Any, reified Success : Any, reified Error : Any> CallDescriptionContainer.call(
    name: String,
    noinline handler: (CallDescription<Request, Success, Error>.() -> Unit)
): CallDescription<Request, Success, Error> {
    return call(name, handler, jacksonTypeRef(), jacksonTypeRef(), jacksonTypeRef())
}

typealias OnCallDescriptionBuildHandler = (CallDescription<*, *, *>) -> Unit


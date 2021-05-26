package dk.sdu.cloud.calls

import dk.sdu.cloud.freeze
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.native.concurrent.SharedImmutable
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

data class AttributeKey<V : Any>(val key: String)

class AttributeContainer {
    private val internalContainer = HashMap<AttributeKey<*>, Any>()

    operator fun <V : Any> set(key: AttributeKey<V>, value: V) {
        internalContainer[key] = value
    }

    fun <V : Any> setOrDelete(key: AttributeKey<V>, value: V?) {
        if (value == null) remove(key)
        else set(key, value)
    }

    fun <V : Any> remove(key: AttributeKey<V>) {
        internalContainer.remove(key)
    }

    operator fun <V : Any> get(key: AttributeKey<V>): V {
        val result = internalContainer[key] ?: throw IllegalArgumentException("No such key!")
        @Suppress("UNCHECKED_CAST")
        return result as V
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
    val requestType: KSerializer<Request>,
    val successType: KSerializer<Success>,
    val errorType: KSerializer<Error>,
    val requestClass: KType,
    val successClass: KType,
    val errorClass: KType,
    val containerRef: CallDescriptionContainer,
) {
    val fullName: String get() = "$namespace.$name"

    override fun toString(): String = "CallDescription($fullName)"
}

abstract class CallDescriptionContainer(val namespace: String) {
    val attributes = AttributeContainer()
    private val _callContainer = ArrayList<CallDescription<*, *, *>>()
    val callContainer: List<CallDescription<*, *, *>>
        get() = _callContainer

    fun <Request : Any, Success : Any, Error : Any> call(
        name: String,
        handler: (CallDescription<Request, Success, Error>.() -> Unit),
        requestType: KSerializer<Request>,
        successType: KSerializer<Success>,
        errorType: KSerializer<Error>,
        requestClass: KType,
        successClass: KType,
        errorClass: KType,
    ): CallDescription<Request, Success, Error> {
        val callDescription = CallDescription(
            name,
            namespace,
            AttributeContainer(),
            requestType,
            successType,
            errorType,
            requestClass,
            successClass,
            errorClass,
            this
        )
        callDescription.handler()
        _callContainer.add(callDescription)
        onBuildHandlers.forEach { it(callDescription) }
        return callDescription
    }

    companion object {
        private val onBuildHandlers = ArrayList<OnCallDescriptionBuildHandler>()

        fun onBuild(handler: OnCallDescriptionBuildHandler) {
            onBuildHandlers.add(handler)
        }
    }
}

inline fun <reified Request : Any, reified Success : Any, reified Error : Any> CallDescriptionContainer.call(
    name: String,
    noinline handler: (CallDescription<Request, Success, Error>.() -> Unit),
): CallDescription<Request, Success, Error> {
    return call(
        name,
        handler,
        fixedSerializer(),
        fixedSerializer(),
        fixedSerializer(),
        typeOf<Request>(),
        typeOf<Success>(),
        typeOf<Error>(),
    )
}

typealias OnCallDescriptionBuildHandler = (CallDescription<*, *, *>) -> Unit

@SharedImmutable
private val serializerLookupTableKey = AttributeKey<Map<KType, KSerializer<*>>>("serializer-lookup-table").freeze()
var CallDescriptionContainer.serializerLookupTable: Map<KType, KSerializer<*>>
    get() = attributes[serializerLookupTableKey]
    set(value) {
        attributes[serializerLookupTableKey] = value
    }

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> serializerEntry(serializer: KSerializer<T>): Pair<KType, KSerializer<T>> {
    return Pair(typeOf<T>(), serializer)
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified Request : Any> CallDescriptionContainer.fixedSerializer(): KSerializer<Request> {
    return try {
        serializer<Request>()
    } catch (ex: SerializationException) {
        @Suppress("UNCHECKED_CAST")
        serializerLookupTable[typeOf<Request>()] as KSerializer<Request>? ?: throw ex
    }
}
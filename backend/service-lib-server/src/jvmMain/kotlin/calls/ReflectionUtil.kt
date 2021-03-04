package dk.sdu.cloud.calls

import com.fasterxml.jackson.core.type.TypeReference
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

val Type.jvmClass: Class<*>
    get() = when {
        this is Class<*> -> this
        this is ParameterizedType -> rawType.jvmClass
        else -> throw IllegalStateException()
    }

val Type.kClass: KClass<*>
    get() = jvmClass.kotlin

val TypeReference<*>.companionInstance: Any?
    get() = kClass.companionObjectInstance

@Suppress("UNCHECKED_CAST")
val TypeReference<*>.kClass: KClass<*>
    get() = type.kClass


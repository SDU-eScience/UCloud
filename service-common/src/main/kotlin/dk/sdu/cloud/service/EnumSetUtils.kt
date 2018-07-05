package dk.sdu.cloud.service

import java.util.HashSet

fun <E : Enum<E>> Set<E>.asInt(): Int {
    var value = 0
    for (item in this) {
        value = value or (1 shl item.ordinal)
    }
    return value
}

fun <E : Enum<E>> Set<E>.asLong(): Long {
    var value = 0L
    for (item in this) {
        value = value or (1L shl item.ordinal)
    }
    return value
}

inline fun <reified E : Enum<E>> Int.asEnumSet(): Set<E> {
    val result = HashSet<E>()
    for (item in enumValues<E>()) {
        if (((1 shl item.ordinal) and this) != 0) {
            result.add(item)
        }
    }
    return result
}

inline fun <reified E : Enum<E>> Long.asEnumSet(): Set<E> {
    val result = HashSet<E>()
    for (item in enumValues<E>()) {
        if (((1L shl item.ordinal) and this) != 0L) {
            result.add(item)
        }
    }
    return result
}

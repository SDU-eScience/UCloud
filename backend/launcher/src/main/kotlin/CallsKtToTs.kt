package dk.sdu.cloud

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.kubernetes.api.Maintenance
import dk.sdu.cloud.app.orchestrator.api.Shells
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Page
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties

private fun getTypeName(type: KType): String {
    val klass = type.classifier as? KClass<*> ?: return "any"
    return when (klass) {
        String::class -> "string"
        Boolean::class -> "boolean"
        List::class, Set::class -> {
            return getTypeName(type.arguments.first().type!!) + "[]"
        }
        Int::class, Short::class, Double::class, Long::class, Byte::class, Float::class -> "number"
        else -> {
            if (!klass.isData && !klass.isSubclassOf(Enum::class)) {
                "/* Unable to convert ${klass.simpleName} */ any"
            } else {
                return klass.simpleName!!
            }
        }
    }
}

private fun writeType(klass: KClass<*>, name: String? = klass.simpleName): List<KClass<*>> {
    val qualifiedName = klass.qualifiedName ?: return emptyList()
    if (qualifiedName.startsWith("kotlin.") || qualifiedName.startsWith("java.")) return emptyList()
    if (klass == Page::class) return emptyList()
    if (klass.isSubclassOf(Enum::class)) {
        print("export enum ${klass.simpleName} {")
        klass.java.enumConstants.forEach {
            print("\n    ")
            print((it as Enum<*>).name)
            print(" = \"")
            print((it as Enum<*>).name)
            print("\",")
        }
        println("\n}")
        return emptyList()
    }
    if (!klass.isData) {
        println("/* Unable to convert $qualifiedName */")
        return emptyList()
    }
    val foundTypes = ArrayList<KClass<*>>()
    print("export interface $name {")
    klass.memberProperties.forEach { prop ->
        print("\n    ")
        print(prop.name)
        if (prop.returnType.isMarkedNullable) {
            print("?")
        }
        print(": ")
        print(getTypeName(prop.returnType))
        print(",")

        val returnClassifier = prop.returnType.classifier as? KClass<*>
        if (returnClassifier != null) foundTypes.add(returnClassifier)
        // NOTE(Dan): We only go one level deep currently
        foundTypes.addAll(
            prop.returnType.arguments.mapNotNull { it.type?.classifier }.filterIsInstance<KClass<*>>()
        )
    }
    println("\n}")
    return foundTypes
}

fun main() {
    val descriptions = listOf<CallDescriptionContainer>(
        // TODO Put call container here
    )

    val classesSeen = HashSet<String>()

    for (description in descriptions) {
        for (call in description.callContainer) {
            // NOTE(Dan): This just assumes that it is all or nothing for all parameters

            if (call.httpOrNull == null) continue

            val usesParams = call.http.params != null
            val usesBody = call.http.body != null
            val path = call.http.path.toKtorTemplate(true).replaceFirst("/api/", "/")
            val requestName = call.name.capitalize() + "Request"
            val responseName = call.name.capitalize() + "Response"
            val method = call.http.method.value.toUpperCase()

            val queue = LinkedList<KClass<*>>()
            val requestType = call.requestType.type
            val successType = call.successType.type
            queue.addAll(writeType(requestType.kClass, requestName))
            classesSeen.addAll(queue.mapNotNull { it.simpleName })
            queue.addAll(writeType(successType.kClass, responseName).filter { it.simpleName !in classesSeen })
            classesSeen.addAll(queue.mapNotNull { it.simpleName })

            if (requestType is ParameterizedType) {
                requestType.actualTypeArguments.forEach { type ->
                    if (type is WildcardType) {
                        queue.addAll(type.upperBounds.filterIsInstance<Class<*>>().map { it.kotlin })
                    }
                }
            }

            if (successType is ParameterizedType) {
                successType.actualTypeArguments.forEach { type ->
                    if (type is WildcardType) {
                        queue.addAll(type.upperBounds.filterIsInstance<Class<*>>().map { it.kotlin })
                    }
                }
            }
            while (queue.isNotEmpty()) {
                val next = queue.pop()
                val newTypes = writeType(next)
                queue.addAll(newTypes.filter { it.qualifiedName !in classesSeen })
                classesSeen.addAll(newTypes.mapNotNull { it.qualifiedName })
            }

            println(
                """
                    export function ${call.name}(
                        request: $requestName
                    ): APICallParameters<$requestName> {
                        return {
                            method: "$method",
                            path: ${if (usesParams) "buildQueryString(" else ""}"$path"${if (usesParams) ", request)" else ""},
                            parameters: request,
                            reloadId: Math.random(),
                            payload: ${if (usesBody) "request" else "undefined"}
                        };
                    }
                """.trimIndent()
            )
        }
    }
}
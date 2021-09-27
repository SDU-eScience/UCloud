package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaField

data class ResponseExample(val statusCode: Int, val description: String)
data class UseCaseReference(val usecase: String, val description: String)

data class GeneratedRemoteProcedureCall(
    var requestType: GeneratedTypeReference,
    var responseType: GeneratedTypeReference,
    var errorType: GeneratedTypeReference,
    val namespace: String,
    val name: String,
    val roles: Set<Role>,
    val responseExamples: List<ResponseExample>,
    val useCaseReferences: List<UseCaseReference>,
    val doc: Documentation,
    val realCall: CallDescription<*, *, *>,
)

fun generateCalls(
    calls: CallDescriptionContainer,
    visitedTypes: LinkedHashMap<String, GeneratedType>
): List<GeneratedRemoteProcedureCall> {
    val containerDocs = calls::class.java.documentation()
    calls::class.members.forEach {
        try {
            if (it.returnType.isSubtypeOf(CallDescription::class.starProjectedType) && it.name != "call") {
                it.call(calls)
            }
        } catch (ex: Throwable) {
            println("Unexpected failure: ${calls} ${it}. ${ex.stackTraceToString()}")
        }
    }

    val genericReplacements = calls::class.generateGenericReplacements().replaceGenerics()

    val allCalls = ArrayList(calls.callContainer)
    val result = allCalls.map { generateCall(it, calls, visitedTypes, containerDocs) }
    for ((name, type) in visitedTypes) {
        when (type) {
            is GeneratedType.Enum -> {
                // Do nothing
            }

            is GeneratedType.Struct -> {
                type.properties = type.properties.replaceGenerics(genericReplacements)
            }

            is GeneratedType.TaggedUnion -> {
                type.baseProperties = type.baseProperties.replaceGenerics(genericReplacements)
            }
        }
    }

    for (call in result) {
        call.requestType = call.requestType.replaceGeneric(genericReplacements)
        call.responseType = call.responseType.replaceGeneric(genericReplacements)
        call.errorType = call.errorType.replaceGeneric(genericReplacements)
    }
    return result
}

fun GeneratedTypeReference.replaceGeneric(replacements: Map<String, GeneratedTypeReference>): GeneratedTypeReference {
    return if (this is GeneratedTypeReference.Structure) {
        val replacedRoot = replacements.getOrDefault(name, this) as GeneratedTypeReference.Structure
        GeneratedTypeReference.Structure(replacedRoot.name, generics.map { it.replaceGeneric(replacements) }, nullable)
    } else {
        this
    }
}

fun List<GeneratedType.Property>.replaceGenerics(
    replacements: Map<String, GeneratedTypeReference>
): List<GeneratedType.Property> {
    return map { GeneratedType.Property(it.name, it.doc, it.type.replaceGeneric(replacements)) }
}

fun HashMap<KTypeParameter, KTypeProjection>.replaceGenerics(): HashMap<String, GeneratedTypeReference> {
    val result = HashMap<String, GeneratedTypeReference>()
    for ((key, value) in entries) {
        val type = GeneratedTypeReference.Structure(
            (value.type?.classifier as? KClass<*>)?.java?.canonicalName
                ?: error("Cannot generate generic replacement for $key $value")
        )
        result[key.name] = type
    }
    return result
}

fun KClass<*>.generateGenericReplacements(
    builder: HashMap<KTypeParameter, KTypeProjection> = HashMap()
): HashMap<KTypeParameter, KTypeProjection> {
    val superType = supertypes.firstOrNull() ?: return builder

    val args = superType.arguments
    val superTypeClassifier = superType.classifier as KClass<*>
    val typeParams = superTypeClassifier.typeParameters

    typeParams.zip(args).forEach { (param, value) ->
        builder[param] = value
    }

    return superTypeClassifier.generateGenericReplacements(builder)
}

fun GeneratedTypeReference.attachOwner(
    owner: KClass<out CallDescriptionContainer>,
    visitedTypes: LinkedHashMap<String, GeneratedType>
) {
    val packageName = owner.java.packageName

    when (this) {
        is GeneratedTypeReference.Array -> valueType.attachOwner(owner, visitedTypes)
        is GeneratedTypeReference.Dictionary -> valueType.attachOwner(owner, visitedTypes)
        is GeneratedTypeReference.Structure -> {
            this.generics.forEach { it.attachOwner(owner, visitedTypes) }
            val typePackageName = this.name.substringBeforeLast('.')
            val generatedStructure = visitedTypes[this.name]
            if (generatedStructure != null) {
                if (generatedStructure is GeneratedType.Struct) {
                    generatedStructure.properties.forEach {
                        it.type.attachOwner(generatedStructure.owner ?: owner, visitedTypes)
                    }
                } else if (generatedStructure is GeneratedType.TaggedUnion) {
                    generatedStructure.baseProperties.forEach {
                        it.type.attachOwner(generatedStructure.owner ?: owner, visitedTypes)
                    }
                }

                if (typePackageName == packageName && !generatedStructure.hasExplicitOwner) {
                    if (generatedStructure.owner != null && generatedStructure.owner != owner) {
                        println(
                            "Ambiguous owner of ${this.name}. " +
                                "You can fix this by attaching " +
                                "@UCloudApiOwnedBy(${generatedStructure.owner!!.java.simpleName}::class) or " +
                                "@UCloudApiOwnedBy(${owner.java.simpleName}::class) to the type."
                        )
                    }

                    generatedStructure.owner = owner
                }
            }
        }

        else -> {
            // Do nothing
        }
    }
}

private fun generateCall(
    call: CallDescription<*, *, *>,
    container: CallDescriptionContainer,
    visitedTypes: LinkedHashMap<String, GeneratedType>,
    containerDocs: Documentation,
): GeneratedRemoteProcedureCall {
    val requestType = traverseType(call.requestClass.javaType, visitedTypes)
        .also { it.attachOwner(container::class, visitedTypes) }
    val responseType = traverseType(call.successClass.javaType, visitedTypes)
        .also { it.attachOwner(container::class, visitedTypes) }
    val errorType = traverseType(call.errorClass.javaType, visitedTypes)
        .also { it.attachOwner(container::class, visitedTypes) }

    val synopsis = call.docOrNull?.summary
    val description = call.docOrNull?.description

    val field = call.field
    val defaultMaturity = containerDocs.maturity
    val overriddenDocs = container.docOverrides[call.fullName]?.docs?.split()
    val fieldDocs = field?.javaField?.documentation(defaultMaturity)

    return GeneratedRemoteProcedureCall(
        requestType,
        responseType,
        errorType,
        call.namespace,
        call.name,
        call.authDescription.roles,
        emptyList(),
        emptyList(),
        Documentation(
            fieldDocs?.deprecated ?: false,
            fieldDocs?.maturity ?: defaultMaturity,
            overriddenDocs?.synopsis ?: synopsis ?: fieldDocs?.synopsis,
            overriddenDocs?.description ?: description ?: fieldDocs?.description,
        ),
        call,
    )
}

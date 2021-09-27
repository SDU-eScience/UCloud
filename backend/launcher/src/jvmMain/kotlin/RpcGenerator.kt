package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.Files
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaField

data class ResponseExample(val statusCode: Int, val description: String)
data class UseCaseReference(val usecase: String, val description: String)

data class GeneratedRemoteProcedureCall(
    val requestType: GeneratedTypeReference,
    val responseType: GeneratedTypeReference,
    val errorType: GeneratedTypeReference,
    val namespace: String,
    val name: String,
    val roles: Set<Role>,
    val responseExamples: List<ResponseExample>,
    val useCaseReferences: List<UseCaseReference>,
    val doc: Documentation,
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
    val allCalls = ArrayList(calls.callContainer)
    return allCalls.map { generateCall(it, calls, visitedTypes, containerDocs) }
}

private fun generateCall(
    call: CallDescription<*, *, *>,
    container: CallDescriptionContainer,
    visitedTypes: LinkedHashMap<String, GeneratedType>,
    containerDocs: Documentation,
): GeneratedRemoteProcedureCall {
    val requestType = traverseType(call.requestClass.javaType, visitedTypes)
    val responseType = traverseType(call.successClass.javaType, visitedTypes)
    val errorType = traverseType(call.errorClass.javaType, visitedTypes)

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
            overriddenDocs?.description ?: description ?: fieldDocs?.description
        )
    )
}

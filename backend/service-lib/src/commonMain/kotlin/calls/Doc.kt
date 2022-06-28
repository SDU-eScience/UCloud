package dk.sdu.cloud.calls

import dk.sdu.cloud.calls.client.FakeOutgoingCall
import dk.sdu.cloud.calls.client.IngoingCallResponse
import kotlin.native.concurrent.SharedImmutable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


const val TYPE_REF = "#TYPEREF#="
const val CALL_REF = "#CALLREF#="
const val CALL_REF_LINK = "#CALLREFLINK#="
const val TYPE_REF_LINK = "#TYPEREFLINK#="

@Retention
@Target(AnnotationTarget.CLASS)
annotation class UCloudApiOwnedBy(
    val owner: KClass<out CallDescriptionContainer>
)

/**
 * The [UCloudApiDoc] annotation is used to annotate request/response types and their properties
 *
 * To document a call please use [UCloudCallDoc] via [CallDescription.documentation].
 */
@Retention
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class UCloudApiDoc(
    @Language("markdown", "", "") val documentation: String,
    val inherit: Boolean = false,
    val importance: Int = 0,
)

data class UCloudApiDocC(
    @Language("markdown", "", "") val documentation: String,
    val inherit: Boolean = false,
    val importance: Int = 0,
)

expect annotation class Language(
    val value: String,
    val prefix: String,
    val suffix: String,
)

interface UCloudCallDoc<R : Any, S : Any, E : Any> {
    val summary: String?
    val description: String?
    val responseExamples: ArrayList<ResponseExample>
    val useCaseReferences: ArrayList<UseCaseReference>

    data class UseCaseReference(val id: String, val description: String)

    data class ResponseExample(
        val statusCode: HttpStatusCode,
        val description: String
    )

    companion object {
        internal val key = AttributeKey<UCloudCallDoc<*, *, *>>("call-doc")
    }
}

class UCloudCallDocBuilder<R : Any, S : Any, E : Any>(val namespace: String) : UCloudCallDoc<R, S, E> {
    override var summary: String? = null
    @Language("markdown", "", "")
    override var description: String? = null
    override val responseExamples = ArrayList<UCloudCallDoc.ResponseExample>()
    override val useCaseReferences = ArrayList<UCloudCallDoc.UseCaseReference>()
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.documentation(
    handler: UCloudCallDocBuilder<R, S, E>.() -> Unit
) {
    attributes[UCloudCallDoc.key] = UCloudCallDocBuilder<R, S, E>(namespace).also { it.handler() }
}

val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.docOrNull: UCloudCallDoc<R, S, E>?
    @Suppress("UNCHECKED_CAST")
    get() = attributes.getOrNull(UCloudCallDoc.key) as UCloudCallDoc<R, S, E>?

fun UCloudCallDocBuilder<*, *, *>.responseExample(statusCode: HttpStatusCode, description: String) {
    responseExamples.add(UCloudCallDoc.ResponseExample(statusCode, description))
}

fun UCloudCallDocBuilder<*, *, *>.useCaseReference(useCaseId: String, description: String) {
    useCaseReferences.add(UCloudCallDoc.UseCaseReference(namespace + "_" + useCaseId, description))
}

expect fun CallDescriptionContainer.docCallRef(
    call: KProperty<CallDescription<*, *, *>>,
    qualified: Boolean? = null,
): String

// NOTE(Dan): Not type-safe to avoid cyclic dependencies
fun CallDescriptionContainer.docNamespaceRef(namespace: String): String {
    return "[`$namespace`](#tag/$namespace)"
}

@SharedImmutable
private val containerTitle = AttributeKey<String>("docTitle")
var CallDescriptionContainer.title: String?
    get() = attributes.getOrNull(containerTitle)
    set(value) {
        if (value == null) {
            attributes.remove(containerTitle)
        } else {
            attributes[containerTitle] = value
        }
    }

@SharedImmutable
private val containerDescription = AttributeKey<String>("docDescription")
var CallDescriptionContainer.description: String?
    get() = attributes.getOrNull(containerDescription)
    set(value) {
        if (value == null) {
            attributes.remove(containerDescription)
        } else {
            attributes[containerDescription] = value
        }
    }

@UCloudApiDoc("RpcDocumentationOverride allows the developer to override documentation of an inherited call")
data class RpcDocumentationOverride(
    val call: CallDescription<*, *, *>,
    val docs: UCloudApiDocC
)

@SharedImmutable
private val docOverridesKey = AttributeKey<MutableMap<String, RpcDocumentationOverride>>("docOverrides")
val CallDescriptionContainer.docOverrides: Map<String, RpcDocumentationOverride>
    get() = attributes.getOrNull(docOverridesKey) ?: emptyMap()

fun CallDescriptionContainer.document(call: CallDescription<*, *, *>, documentation: UCloudApiDocC) {
    val overrides = attributes.getOrNull(docOverridesKey) ?: run {
        val newMap = HashMap<String, RpcDocumentationOverride>()
        attributes[docOverridesKey] = newMap
        newMap
    }

    overrides[call.fullName] = RpcDocumentationOverride(call, documentation)
}

@SharedImmutable
private val useCasesKey = AttributeKey<MutableList<UseCase>>("useCases")
val CallDescriptionContainer.useCases: List<UseCase>
    get() = attributes.getOrNull(useCasesKey) ?: emptyList()

fun CallDescriptionContainer.useCase(
    id: String,
    title: String,
    frequencyOfUse: UseCase.FrequencyOfUse = UseCase.FrequencyOfUse.COMMON,
    trigger: String? = null,
    preConditions: List<String> = emptyList(),
    postConditions: List<String> = emptyList(),
    flow: MutableList<UseCaseNode>.() -> Unit,
) {
    val useCase = UseCase(namespace + "_" + id, title, frequencyOfUse, trigger, preConditions, postConditions,
        ArrayList<UseCaseNode>().apply(flow))

    val allUseCases = attributes.getOrNull(useCasesKey) ?: run {
        val newList = ArrayList<UseCase>()
        attributes[useCasesKey] = newList
        newList
    }

    allUseCases.add(useCase)
}

data class UseCase(
    val id: String,
    val title: String,
    val frequencyOfUse: FrequencyOfUse,
    val trigger: String?,
    val preConditions: List<String>,
    val postConditions: List<String>,
    val nodes: List<UseCaseNode>,
) {
    enum class FrequencyOfUse {
        COMMON,
        OCCASIONAL,
        RARE,
        ONE_TIME
    }
}

fun MutableList<UseCaseNode>.ucloudCore(): UseCaseNode.Actor {
    return UseCaseNode.Actor("ucloud", "The UCloud/Core service user").also { add(it) }
}

fun MutableList<UseCaseNode>.provider(): UseCaseNode.Actor {
    return UseCaseNode.Actor("provider", "The provider").also { add(it) }
}

fun MutableList<UseCaseNode>.administrator(): UseCaseNode.Actor {
    return UseCaseNode.Actor("admin", "A UCloud administrator").also { add(it) }
}

fun MutableList<UseCaseNode>.guest(details: String? = null): UseCaseNode.Actor {
    return UseCaseNode.Actor(
        if (details == null) "guest" else "guest ($details)",
        "An unauthenticated user"
    ).also { add(it) }
}

fun MutableList<UseCaseNode>.basicUser(): UseCaseNode.Actor {
    return UseCaseNode.Actor("user", "An authenticated user").also { add(it) }
}

fun MutableList<UseCaseNode>.actor(name: String, description: String): UseCaseNode.Actor {
    return UseCaseNode.Actor(name, description).also { add(it) }
}

fun MutableList<UseCaseNode>.comment(comment: String) {
    add(UseCaseNode.Comment(comment))
}

fun <R : Any, S : Any> MutableList<UseCaseNode>.success(
    call: CallDescription<R, S, *>,
    request: R,
    response: S,
    actor: UseCaseNode.Actor,
    name: String? = null,
) {
    add(UseCaseNode.Call(
        call,
        request,
        IngoingCallResponse.Ok(
            response,
            HttpStatusCode.OK,
            FakeOutgoingCall
        ),
        actor,
        name,
    ))
}

fun <R : Any, E : Any> MutableList<UseCaseNode>.failure(
    call: CallDescription<R, *, E>,
    request: R,
    error: E?,
    status: HttpStatusCode,
    actor: UseCaseNode.Actor,
    name: String? = null,
) {
    add(UseCaseNode.Call(
        call,
        request,
        IngoingCallResponse.Error(
            error,
            status,
            FakeOutgoingCall
        ),
        actor,
        name
    ))
}

fun <R : Any, S : Any, E : Any> MutableList<UseCaseNode>.subscription(
    call: CallDescription<R, S, E>,
    request: R,
    actor: UseCaseNode.Actor,
    protocol: MutableList<UseCaseNode.RequestOrResponse<R, S, E>>.() -> Unit,
) {
    add(UseCaseNode.Subscription<R, S, E>(
        call,
        request,
        mutableListOf<UseCaseNode.RequestOrResponse<R, S, E>>().apply(protocol),
        actor,
    ))
}

fun <R : Any, S : Any, E : Any> MutableList<UseCaseNode.RequestOrResponse<R, S, E>>.request(request: R) {
    add(UseCaseNode.RequestOrResponse.Request(request))
}

fun <R : Any, S : Any, E : Any> MutableList<UseCaseNode.RequestOrResponse<R, S, E>>.success(
    success: S,
    statusCode: HttpStatusCode = HttpStatusCode.OK
) {
    add(UseCaseNode.RequestOrResponse.Response(IngoingCallResponse.Ok(success, statusCode, FakeOutgoingCall)))
}

fun <R : Any, S : Any, E : Any> MutableList<UseCaseNode.RequestOrResponse<R, S, E>>.error(
    statusCode: HttpStatusCode,
    error: E? = null
) {
    add(UseCaseNode.RequestOrResponse.Error(IngoingCallResponse.Error(error, statusCode, FakeOutgoingCall)))
}

sealed class UseCaseNode {
    class Actor(val name: String, val description: String) : UseCaseNode()

    class Call<R : Any, S : Any, E : Any>(
        val call: CallDescription<R, S, E>,
        val request: R,
        val response: IngoingCallResponse<S, E>,
        val actor: Actor,
        val name: String? = null,
    ) : UseCaseNode()

    sealed class RequestOrResponse<R : Any, S : Any, E : Any> {
        class Request<R : Any, S : Any, E : Any>(val request: R) : RequestOrResponse<R, S, E>()
        class Response<R : Any, S : Any, E : Any>(val response: IngoingCallResponse.Ok<S, E>) : RequestOrResponse<R, S, E>()
        class Error<R : Any, S : Any, E : Any>(val error: IngoingCallResponse.Error<S, E>) : RequestOrResponse<R, S, E>()
    }

    class Subscription<R : Any, S : Any, E : Any>(
        val call: CallDescription<R, S, E>,
        val request: R,
        val messages: List<RequestOrResponse<R, S, E>>,
        val actor: Actor
    ) : UseCaseNode()

    class Comment(val comment: String) : UseCaseNode()

    class SourceCode(val language: Language, val code: String) : UseCaseNode()

    enum class Language {
        KOTLIN,
        TYPESCRIPT
    }
}

sealed class ProviderApiRequirements {
    object Optional : ProviderApiRequirements()
    class List(val conditions: kotlin.collections.List<String>) : ProviderApiRequirements()
    object Mandatory : ProviderApiRequirements()
}

fun CallDescriptionContainer.documentProviderCall(
    call: CallDescription<*, *, *>,
    endUserCall: CallDescription<*, *, *>,
    requirements: ProviderApiRequirements,
    details: String = ""
) {
    val docs = providerDescriptionDocs(endUserCall, requirements, details)
    document(call, UCloudApiDocC("${docs.first}\n\n${docs.second}"))
}

fun UCloudCallDocBuilder<*, *, *>.providerDescription(
    endUserCall: CallDescription<*, *, *>,
    requirements: ProviderApiRequirements,
    details: String = ""
) {
    val docs = providerDescriptionDocs(endUserCall, requirements, details)
    summary = docs.first
    description = docs.second
}

private fun providerDescriptionDocs(
    endUserCall: CallDescription<*, *, *>,
    requirements: ProviderApiRequirements,
    details: String
): Pair<String?, String> {
    val summary = endUserCall.docOrNull?.summary
    val description = buildString {
        if (details.isNotEmpty()) {
            appendLine("---")
            appendLine()
        }

        append("__Implementation requirements:__ ")
        when (requirements) {
            ProviderApiRequirements.Optional -> appendLine("Optional")
            ProviderApiRequirements.Mandatory -> appendLine("Mandatory")
            is ProviderApiRequirements.List -> {
                appendLine()
                for (condition in requirements.conditions) {
                    appendLine(" - $condition")
                }
            }
        }

        appendLine()
        appendLine("For more information, see the end-user API ($CALL_REF ${endUserCall.fullName})")

        if (details.isNotEmpty()) {
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(details)
        }
    }
    return Pair(summary, description)
}

@RequiresOptIn
annotation class UCloudApiExampleValue

sealed class DocVisualization {
    abstract var estimatedHeight: Int

    data class Card(
        val title: String,
        val lines: List<DocStatLine>,
        val children: List<DocVisualization>,
        override var estimatedHeight: Int = -1
    ) : DocVisualization()

    data class Inline(val value: String, override var estimatedHeight: Int = -1) : DocVisualization()

    data class Placeholder(val value: Any?, override var estimatedHeight: Int = -1) : DocVisualization()
}

data class DocStatLine(val stats: List<DocStat>) {
    companion object {
        fun of(vararg pairs: Pair<String, DocVisualization>): DocStatLine {
            return DocStatLine(pairs.map { DocStat(it.first, it.second) })
        }
    }
}
data class DocStat(val name: String, val value: DocVisualization)

interface DocVisualizable {
    fun visualize(): DocVisualization
}

fun visualizeValue(value: Any?): DocVisualization = DocVisualization.Placeholder(value)

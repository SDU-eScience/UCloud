package dk.sdu.cloud.calls

import dk.sdu.cloud.calls.client.FakeOutgoingCall
import dk.sdu.cloud.calls.client.IngoingCallResponse
import io.ktor.http.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


const val TYPE_REF = "#TYPEREF#="
const val CALL_REF = "#CALLREF#="
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

class UCloudCallDocBuilder<R : Any, S : Any, E : Any> : UCloudCallDoc<R, S, E> {
    override var summary: String? = null
    @Language("markdown", "", "")
    override var description: String? = null
    override val responseExamples = ArrayList<UCloudCallDoc.ResponseExample>()
    override val useCaseReferences = ArrayList<UCloudCallDoc.UseCaseReference>()
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.documentation(
    handler: UCloudCallDocBuilder<R, S, E>.() -> Unit
) {
    attributes[UCloudCallDoc.key] = UCloudCallDocBuilder<R, S, E>().also { it.handler() }
}

val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.docOrNull: UCloudCallDoc<R, S, E>?
    @Suppress("UNCHECKED_CAST")
    get() = attributes.getOrNull(UCloudCallDoc.key) as UCloudCallDoc<R, S, E>?

fun UCloudCallDocBuilder<*, *, *>.responseExample(statusCode: HttpStatusCode, description: String) {
    responseExamples.add(UCloudCallDoc.ResponseExample(statusCode, description))
}

fun UCloudCallDocBuilder<*, *, *>.useCaseReference(useCaseId: String, description: String) {
    useCaseReferences.add(UCloudCallDoc.UseCaseReference(useCaseId, description))
}

expect fun CallDescriptionContainer.docCallRef(
    call: KProperty<CallDescription<*, *, *>>,
    qualified: Boolean? = null,
): String

// NOTE(Dan): Not type-safe to avoid cyclic dependencies
fun CallDescriptionContainer.docNamespaceRef(namespace: String): String {
    return "[`$namespace`](#tag/$namespace)"
}

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
    val useCase = UseCase(id, title, frequencyOfUse, trigger, preConditions, postConditions,
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

fun MutableList<UseCaseNode>.basicUser(): UseCaseNode.Actor {
    return UseCaseNode.Actor("user", "An authenticated user")
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

sealed class UseCaseNode {
    class Actor(val name: String, val description: String) : UseCaseNode()

    class Call<R : Any, S : Any, E : Any>(
        val call: CallDescription<R, S, E>,
        val request: R,
        val response: IngoingCallResponse<S, E>,
        val actor: Actor,
        val name: String? = null,
    ) : UseCaseNode()

    class Comment(val comment: String) : UseCaseNode()

    class SourceCode(val language: Language, val code: String) : UseCaseNode()

    enum class Language {
        KOTLIN,
        TYPESCRIPT
    }
}

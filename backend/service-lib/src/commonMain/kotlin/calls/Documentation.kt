package dk.sdu.cloud.calls

import dk.sdu.cloud.calls.client.IngoingCallResponse
import io.ktor.http.*
import kotlin.reflect.KProperty

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

expect annotation class Language(
    val value: String,
    val prefix: String,
    val suffix: String,
)

interface UCloudCallDoc<R : Any, S : Any, E : Any> {
    val summary: String?
    val description: String?
    val examples: ArrayList<Example<R, S, E>>
    val errors: ArrayList<Error<E>>

    interface Example<R : Any, S : Any, E : Any>{
        val name: String
        val summary: String?
        val description: String?
        val statusCode: HttpStatusCode
        val request: R?
        val response: S?
        val error: E?
    }

    interface Error<E> {
        val statusCode: HttpStatusCode
        val description: String?
    }

    companion object {
        internal val key = AttributeKey<UCloudCallDoc<*, *, *>>("call-doc")
    }
}

class UCloudCallDocBuilder<R : Any, S : Any, E : Any> : UCloudCallDoc<R, S, E> {
    override var summary: String? = null
    @Language("markdown", "", "")
    override var description: String? = null
    override val examples = ArrayList<UCloudCallDoc.Example<R, S, E>>()
    override val errors = ArrayList<UCloudCallDoc.Error<E>>()

    class ExampleBuilder<R : Any, S : Any, E : Any>(override var name: String) : UCloudCallDoc.Example<R, S, E> {
        override var summary: String? = null
        @Language("markdown", "", "")
        override var description: String? = null
        override var statusCode: HttpStatusCode = HttpStatusCode.OK
        override var request: R? = null
        override var response: S? = null
        override var error: E? = null
    }

    class ErrorBuilder<E> : UCloudCallDoc.Error<E> {
        override var statusCode: HttpStatusCode = HttpStatusCode.BadRequest
        @Language("markdown", "", "")
        override var description: String? = null
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.documentation(
    handler: UCloudCallDocBuilder<R, S, E>.() -> Unit
) {
    attributes[UCloudCallDoc.key] = UCloudCallDocBuilder<R, S, E>().also { it.handler() }
}

val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.docOrNull: UCloudCallDoc<R, S, E>?
    @Suppress("UNCHECKED_CAST")
    get() = attributes.getOrNull(UCloudCallDoc.key) as UCloudCallDoc<R, S, E>?

fun <R : Any, S : Any, E : Any> UCloudCallDocBuilder<R, S, E>.example(
    name: String,
    handler: UCloudCallDocBuilder.ExampleBuilder<R, S, E>.() -> Unit
) {
    examples.add(UCloudCallDocBuilder.ExampleBuilder<R, S, E>(name).also(handler))
}

fun <E : Any> UCloudCallDocBuilder<*, *, E>.error(handler: UCloudCallDocBuilder.ErrorBuilder<E>.() -> Unit) {
    errors.add(UCloudCallDocBuilder.ErrorBuilder<E>().also(handler))
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

private val useCasesKey = AttributeKey<MutableList<UseCase>>("useCases")
val CallDescriptionContainer.useCases: List<UseCase>
    get() = attributes.getOrNull(useCasesKey) ?: emptyList()

fun CallDescriptionContainer.useCase(
    title: String,
    frequencyOfUse: UseCase.FrequencyOfUse = UseCase.FrequencyOfUse.COMMON,
    trigger: String? = null,
    preConditions: List<String> = emptyList(),
    postConditions: List<String> = emptyList(),
    flow: MutableList<UseCaseNode>.() -> Unit,
) {
    val useCase = UseCase(title, frequencyOfUse, trigger, preConditions, postConditions,
        ArrayList<UseCaseNode>().apply(flow))

    val allUseCases = attributes.getOrNull(useCasesKey) ?: run {
        val newList = ArrayList<UseCase>()
        attributes[useCasesKey] = newList
        newList
    }

    allUseCases.add(useCase)
}

data class UseCase(
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

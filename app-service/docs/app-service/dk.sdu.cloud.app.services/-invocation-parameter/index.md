[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [InvocationParameter](./index.md)

# InvocationParameter

`sealed class InvocationParameter`

### Functions

| Name | Summary |
|---|---|
| [buildInvocationSnippet](build-invocation-snippet.md) | `abstract fun buildInvocationSnippet(parameters: `[`Map`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)`<`[`ApplicationParameter`](../../dk.sdu.cloud.app.api/-application-parameter/index.md)`<*>, `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`?>): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`?` |

### Inheritors

| Name | Summary |
|---|---|
| [BooleanFlagParameter](../-boolean-flag-parameter/index.md) | `class BooleanFlagParameter : `[`InvocationParameter`](./index.md) |
| [VariableInvocationParameter](../-variable-invocation-parameter/index.md) | `class VariableInvocationParameter : `[`InvocationParameter`](./index.md) |
| [WordInvocationParameter](../-word-invocation-parameter/index.md) | `class WordInvocationParameter : `[`InvocationParameter`](./index.md) |

[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [ApplicationDescription](./index.md)

# ApplicationDescription

`data class ApplicationDescription`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `ApplicationDescription(tool: `[`NameAndVersion`](../-name-and-version/index.md)`, info: `[`NameAndVersion`](../-name-and-version/index.md)`, authors: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>, prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, createdAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, modifiedAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, invocation: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`InvocationParameter`](../../dk.sdu.cloud.app.services/-invocation-parameter/index.md)`>, parameters: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationParameter`](../-application-parameter/index.md)`<*>>, outputFileGlobs: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>)` |

### Properties

| Name | Summary |
|---|---|
| [authors](authors.md) | `val authors: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [createdAt](created-at.md) | `val createdAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [description](description.md) | `val description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [info](info.md) | `val info: `[`NameAndVersion`](../-name-and-version/index.md) |
| [invocation](invocation.md) | `val invocation: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`InvocationParameter`](../../dk.sdu.cloud.app.services/-invocation-parameter/index.md)`>` |
| [modifiedAt](modified-at.md) | `val modifiedAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [outputFileGlobs](output-file-globs.md) | `val outputFileGlobs: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [parameters](parameters.md) | `val parameters: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationParameter`](../-application-parameter/index.md)`<*>>` |
| [prettyName](pretty-name.md) | `val prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [tool](tool.md) | `val tool: `[`NameAndVersion`](../-name-and-version/index.md) |

### Functions

| Name | Summary |
|---|---|
| [toSummary](to-summary.md) | `fun toSummary(): `[`ApplicationSummary`](../-application-summary/index.md) |

[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [ToolDescription](./index.md)

# ToolDescription

`data class ToolDescription`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `ToolDescription(info: `[`NameAndVersion`](../-name-and-version/index.md)`, container: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, defaultNumberOfNodes: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, defaultTasksPerNode: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`, defaultMaxTime: `[`SimpleDuration`](../-simple-duration/index.md)`, requiredModules: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>, authors: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>, prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, createdAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, modifiedAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)`, description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, backend: `[`ToolBackend`](../-tool-backend/index.md)` = ToolBackend.SINGULARITY)` |

### Properties

| Name | Summary |
|---|---|
| [authors](authors.md) | `val authors: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [backend](backend.md) | `val backend: `[`ToolBackend`](../-tool-backend/index.md) |
| [container](container.md) | `val container: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [createdAt](created-at.md) | `val createdAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [defaultMaxTime](default-max-time.md) | `val defaultMaxTime: `[`SimpleDuration`](../-simple-duration/index.md) |
| [defaultNumberOfNodes](default-number-of-nodes.md) | `val defaultNumberOfNodes: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [defaultTasksPerNode](default-tasks-per-node.md) | `val defaultTasksPerNode: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [description](description.md) | `val description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [info](info.md) | `val info: `[`NameAndVersion`](../-name-and-version/index.md) |
| [modifiedAt](modified-at.md) | `val modifiedAt: `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [prettyName](pretty-name.md) | `val prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [requiredModules](required-modules.md) | `val requiredModules: `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |

[app-service](../../../index.md) / [dk.sdu.cloud.app.api](../../index.md) / [AppRequest](../index.md) / [Start](./index.md)

# Start

`data class Start : `[`AppRequest`](../index.md)

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `Start(application: `[`NameAndVersion`](../../-name-and-version/index.md)`, parameters: `[`Map`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>, numberOfNodes: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`? = null, tasksPerNode: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`? = null, maxTime: `[`SimpleDuration`](../../-simple-duration/index.md)`? = null)` |

### Properties

| Name | Summary |
|---|---|
| [application](application.md) | `val application: `[`NameAndVersion`](../../-name-and-version/index.md) |
| [maxTime](max-time.md) | `val maxTime: `[`SimpleDuration`](../../-simple-duration/index.md)`?` |
| [numberOfNodes](number-of-nodes.md) | `val numberOfNodes: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`?` |
| [parameters](parameters.md) | `val parameters: `[`Map`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>` |
| [tasksPerNode](tasks-per-node.md) | `val tasksPerNode: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`?` |

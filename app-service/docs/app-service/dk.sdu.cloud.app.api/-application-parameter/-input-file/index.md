[app-service](../../../index.md) / [dk.sdu.cloud.app.api](../../index.md) / [ApplicationParameter](../index.md) / [InputFile](./index.md)

# InputFile

`data class InputFile : `[`ApplicationParameter`](../index.md)`<`[`FileTransferDescription`](../../-file-transfer-description/index.md)`>`

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `InputFile(name: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, optional: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)`, defaultValue: `[`FileTransferDescription`](../../-file-transfer-description/index.md)`? = null, prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)` = name, description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)` = "")` |

### Properties

| Name | Summary |
|---|---|
| [defaultValue](default-value.md) | `val defaultValue: `[`FileTransferDescription`](../../-file-transfer-description/index.md)`?` |
| [description](description.md) | `val description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [name](name.md) | `val name: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [optional](optional.md) | `val optional: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [prettyName](pretty-name.md) | `val prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

### Functions

| Name | Summary |
|---|---|
| [internalMap](internal-map.md) | `fun internalMap(inputParameter: `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`): `[`FileTransferDescription`](../../-file-transfer-description/index.md) |
| [toInvocationArgument](to-invocation-argument.md) | `fun toInvocationArgument(entry: `[`FileTransferDescription`](../../-file-transfer-description/index.md)`): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

### Inherited Functions

| Name | Summary |
|---|---|
| [map](../map.md) | `fun map(inputParameter: `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`?): `[`V`](../index.md#V)`?` |

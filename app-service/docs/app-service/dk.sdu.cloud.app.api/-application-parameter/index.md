[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [ApplicationParameter](./index.md)

# ApplicationParameter

`sealed class ApplicationParameter<V : `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`>`

### Types

| Name | Summary |
|---|---|
| [Bool](-bool/index.md) | `data class Bool : `[`ApplicationParameter`](./index.md)`<`[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)`>` |
| [FloatingPoint](-floating-point/index.md) | `data class FloatingPoint : `[`ApplicationParameter`](./index.md)`<`[`Double`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/index.html)`>` |
| [InputFile](-input-file/index.md) | `data class InputFile : `[`ApplicationParameter`](./index.md)`<`[`FileTransferDescription`](../-file-transfer-description/index.md)`>` |
| [Integer](-integer/index.md) | `data class Integer : `[`ApplicationParameter`](./index.md)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`>` |
| [Text](-text/index.md) | `data class Text : `[`ApplicationParameter`](./index.md)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |

### Properties

| Name | Summary |
|---|---|
| [defaultValue](default-value.md) | `abstract val defaultValue: `[`V`](index.md#V)`?` |
| [description](description.md) | `abstract val description: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [name](name.md) | `abstract val name: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [optional](optional.md) | `abstract val optional: `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [prettyName](pretty-name.md) | `abstract val prettyName: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

### Functions

| Name | Summary |
|---|---|
| [internalMap](internal-map.md) | `abstract fun internalMap(inputParameter: `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`): `[`V`](index.md#V) |
| [map](map.md) | `fun map(inputParameter: `[`Any`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/index.html)`?): `[`V`](index.md#V)`?` |
| [toInvocationArgument](to-invocation-argument.md) | `abstract fun toInvocationArgument(entry: `[`V`](index.md#V)`): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

### Inheritors

| Name | Summary |
|---|---|
| [Bool](-bool/index.md) | `data class Bool : `[`ApplicationParameter`](./index.md)`<`[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)`>` |
| [FloatingPoint](-floating-point/index.md) | `data class FloatingPoint : `[`ApplicationParameter`](./index.md)`<`[`Double`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/index.html)`>` |
| [InputFile](-input-file/index.md) | `data class InputFile : `[`ApplicationParameter`](./index.md)`<`[`FileTransferDescription`](../-file-transfer-description/index.md)`>` |
| [Integer](-integer/index.md) | `data class Integer : `[`ApplicationParameter`](./index.md)`<`[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)`>` |
| [Text](-text/index.md) | `data class Text : `[`ApplicationParameter`](./index.md)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |

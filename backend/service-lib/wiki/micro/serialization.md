Serialization in UCloud is provided by the [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
library.

A pre-configured serialization object available in `service-lib` and is exported as `defaultMapper` in
the `dk.sdu.cloud` package. The `defaultMapper` uses `JSON` as its data format. This mapper uses the following
configuration:

- Unknown properties are allowed
- The mapper is configured to be as lenient as possible

The reason for this configuration is to be as relaxed as possible in order to improve backwards-compatibility.

## Sealed Classes

For a variety of reasons, including security, is generally not recommended that you use large class-hierarchies for
request/response types. The one exception to this rule is
[sealed classes](https://kotlinlang.org/docs/reference/sealed-classes.html). This introduces a problem on the
client-side of how to determine the correct type. To solve this problem, you must add annotations to the sealed class
which tell Jackson to annotate the resulting JSON with a new key-value pair which include a type-hint.

## Examples

__Example:__ Using serializable sealed classes

```kotlin
@Serializable
sealed class LongRunningResponse<T> {
    @Serializable
    @SerialName("timeout")
    data class Timeout<T>(
        val why: String = "The operation has timed out and will continue in the background"
    ) : LongRunningResponse<T>()

    @Serializable
    @SerialName("result")
    data class Result<T>(
        val item: T
    ) : LongRunningResponse<T>()
}
```

__Example:__ Using the `defaultMapper` instance to parse a JSON object

```kotlin
val result = defaultMapper.decodeFromString(PageV2.serializer(Tool.serializer()), jsonText)
```

__Example:__ Using the `defaultMapper` instance to serialize an object to JSON text

```kotlin
defaultMapper.encodeToString(Project.serializer(), project)
```

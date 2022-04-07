---

__📝 NOTE:__ This page is currently out-of-date due to a reason change to the `kotlinx.serialization` library. The same
principals as mentioned here are still true.

---

Serialization in UCloud is provided by the [jackson](https://github.com/FasterXML/jackson) library. We use the
following modules, on top of the core jackson module:

- [jackson-module-kotlin](https://github.com/FasterXML/jackson-module-kotlin)
- [jackson-dataformat-yaml](https://github.com/FasterXML/jackson-dataformats-text)
 
A jackson mapper is available in `service-lib` and is exported as `defaultMapper` in the `dk.sdu.cloud` package.
The `defaultMapper` uses `JSON` as its data format. This mapper uses the following configuration:

| Config | Description |
|--------|-------------|
| `DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES = false` | Don't fail if an ignored property is encountered  |
| `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false` | Don't fail if a new property is introduced in the API |
| `DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY = true` | Provides flexibility in terms of compatibility |
| `JsonParser.Feature.ALLOW_TRAILING_COMMA = true` | Provides flexibility in terms of compatibility |
| `JsonParser.Feature.ALLOW_COMMENTS = true` | Provides flexibility in terms of compatibility |

The reason for this configuration is to be as relaxed as possible in order to improve backwards-compatibility.

## Sealed Classes

For a variety of reasons, including security, is generally not recommended that you use large class-hierarchies for
request/response types. The one exception to this rule is
[sealed classes](https://kotlinlang.org/docs/reference/sealed-classes.html). This introduces a problem on the
client-side of how to determine the correct type. To solve this problem, you must add annotations to the sealed class
which tell Jackson to annotate the resulting JSON with a new key-value pair which include a type-hint.

## Examples

__Example:__ Using sealed classes with Jackson

```kotlin
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = LongRunningResponse.Timeout::class, name = "timeout"),
    JsonSubTypes.Type(value = LongRunningResponse.Result::class, name = "result")
)
sealed class LongRunningResponse<T> {
    data class Timeout<T>(
        val why: String = "The operation has timed out and will continue in the background"
    ) : LongRunningResponse<T>()

    data class Result<T>(
        val item: T
    ) : LongRunningResponse<T>()
}
```

__Example:__ Using the `defaultMapper` instance to parse a JSON object

```kotlin
val result = defaultMapper.readValue<Page<Tool>>(jsonText)
```

__Example:__ Using the `defaultMapper` instance to serialize an object to JSON

```kotlin
defaultMapper.writeValueAsString(SlackMessage(message))
```

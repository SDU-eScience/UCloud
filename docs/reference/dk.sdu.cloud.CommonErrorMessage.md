[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Core Types](/docs/developer-guide/core/types.md)

# `CommonErrorMessage`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A generic error message_

```kotlin
data class CommonErrorMessage(
    val why: String,
    val errorCode: String?,
)
```
UCloud uses HTTP status code for all error messages. In addition and if possible, UCloud will include a message
using a common format. Note that this is not guaranteed to be included in case of a failure somewhere else in
the network stack. For example, UCloud's load balancer might not be able to contact the backend at all. In
such a case UCloud will _not_ include a more detailed error message.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>why</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Human readable description of why the error occurred. This value is generally not stable.
</summary>





</details>

<details>
<summary>
<code>errorCode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Machine readable description of why the error occurred. This value is stable and can be relied upon.
</summary>





</details>



</details>



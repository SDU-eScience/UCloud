[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# `JobsProviderFollowResponse`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A message emitted by the Provider in a follow session_

```kotlin
data class JobsProviderFollowResponse(
    val streamId: String,
    val rank: Int,
    val stdout: String?,
    val stderr: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>streamId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique ID for this follow session, the same identifier should be used for the entire session
</summary>



We recommend that Providers generate a UUID or similar for this ID.


</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The rank of the node (0-indexed)
</summary>



Valid values range from 0 (inclusive) until [`specification.replicas`](#) (exclusive)


</details>

<details>
<summary>
<code>stdout</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> New messages from stdout (if any)
</summary>



The bytes from stdout, of the running process, should be interpreted as UTF-8. If the stream contains invalid
bytes then these should be ignored and skipped.

See https://linux.die.net/man/3/stdout for more information.


</details>

<details>
<summary>
<code>stderr</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> New messages from stderr (if any)
</summary>



The bytes from stdout, of the running process, should be interpreted as UTF-8. If the stream contains invalid
bytes then these should be ignored and skipped.

See https://linux.die.net/man/3/stderr for more information.


</details>



</details>



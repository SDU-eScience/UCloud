[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Slack](/docs/developer-guide/core/communication/slack.md)

# `Ticket`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Ticket(
    val requestId: String,
    val principal: SecurityPrincipal,
    val userAgent: String,
    val subject: String,
    val message: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>requestId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>principal</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.SecurityPrincipal.md'>SecurityPrincipal</a></code></code>
</summary>





</details>

<details>
<summary>
<code>userAgent</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>message</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# `ProvidersRequestApprovalResponse.AwaitingAdministratorApproval`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Response type used as part of the approval process_

```kotlin
data class AwaitingAdministratorApproval(
    val token: String,
    val type: String /* "awaiting_admin_approval" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "awaiting_admin_approval" */</code></code> The type discriminator
</summary>





</details>



</details>



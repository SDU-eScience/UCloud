[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# `ProvidersRequestApprovalRequest.Information`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Request type used as part of the approval process, provides contact information_

```kotlin
data class Information(
    val specification: ProviderSpecification,
    val type: String /* "information" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>specification</code>: <code><code><a href='#providerspecification'>ProviderSpecification</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "information" */</code></code> The type discriminator
</summary>





</details>



</details>



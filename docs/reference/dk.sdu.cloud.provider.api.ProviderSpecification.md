[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# `ProviderSpecification`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The specification of a Provider contains basic (network) contact information_

```kotlin
data class ProviderSpecification(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int?,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>domain</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>https</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>port</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>



</details>



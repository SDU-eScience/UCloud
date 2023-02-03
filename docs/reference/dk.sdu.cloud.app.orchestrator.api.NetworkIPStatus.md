[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Public IPs (NetworkIP)](/docs/developer-guide/orchestration/compute/ips.md)

# `NetworkIPStatus`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The status of an `NetworkIP`_

```kotlin
data class NetworkIPStatus(
    val state: NetworkIPState,
    val boundTo: List<String>?,
    val ipAddress: String?,
    val resolvedSupport: ResolvedSupport<Product.NetworkIP, NetworkIPSupport>?,
    val resolvedProduct: Product.NetworkIP?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#networkipstate'>NetworkIPState</a></code></code>
</summary>





</details>

<details>
<summary>
<code>boundTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The ID of the `Job` that this `NetworkIP` is currently bound to
</summary>





</details>

<details>
<summary>
<code>ipAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The externally accessible IP address allocated to this `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>, <a href='#networkipsupport'>NetworkIPSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



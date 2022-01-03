[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Public Links (Ingress)](/docs/developer-guide/orchestration/compute/ingress.md)

# `IngressStatus`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The status of an `Ingress`_

```kotlin
data class IngressStatus(
    val boundTo: List<String>?,
    val state: IngressState,
    val resolvedSupport: ResolvedSupport<Product.Ingress, IngressSupport>?,
    val resolvedProduct: Product.Ingress?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>boundTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The ID of the `Job` that this `Ingress` is currently bound to
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#ingressstate'>IngressState</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Ingress.md'>Product.Ingress</a>, <a href='#ingresssupport'>IngressSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Ingress.md'>Product.Ingress</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



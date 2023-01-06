[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Shares](/docs/developer-guide/orchestration/storage/shares.md)

# `Share.Status`


[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class Status(
    val shareAvailableAt: String?,
    val state: Share.State,
    val resolvedSupport: ResolvedSupport<Product.Storage, ShareSupport>?,
    val resolvedProduct: Product.Storage?,
)
```
The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>shareAvailableAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#share.state'>Share.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='#sharesupport'>ShareSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# `ExampleResource.Status`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class Status(
    val state: ExampleResource.State,
    val value: Int,
    val resolvedSupport: ResolvedSupport<Product, ExampleResourceSupport>?,
    val resolvedProduct: Product?,
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
<code>state</code>: <code><code><a href='#exampleresource.state'>ExampleResource.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='#resolvedsupport'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#exampleresourcesupport'>ExampleResourceSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



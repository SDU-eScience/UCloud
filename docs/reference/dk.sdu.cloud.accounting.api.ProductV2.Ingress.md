[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# `ProductV2.Ingress`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class Ingress(
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val description: String?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val usage: Long?,
    val type: String /* "ingress" */,
)
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>





</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "ingress" */</code></code> The type discriminator
</summary>





</details>



</details>



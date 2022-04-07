[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# `Product.Synchronization`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class Synchronization(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "synchronization" */,
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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>



For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A version number for this Product, managed by UCloud
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "synchronization" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



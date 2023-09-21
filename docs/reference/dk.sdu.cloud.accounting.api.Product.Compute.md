[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# `Product.Compute`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_A compute Product_

```kotlin
data class Compute(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val cpu: Int?,
    val memoryInGigs: Int?,
    val gpu: Int?,
    val cpuModel: String?,
    val memoryModel: String?,
    val gpuModel: String?,
    val version: Int?,
    val freeToUse: Boolean?,
    val allowAllocationRequestsFrom: AllocationRequestsGroup?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val maxUsableBalance: Long?,
    val type: String /* "compute" */,
)
```
| Unit | API |
|------|-----|
| Measured in hyper-threaded cores (vCPU) | [Click here](/docs/developer-guide/orchestration/compute/jobs.md) |

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
<code>cpu</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>memoryInGigs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>gpu</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>cpuModel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>memoryModel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>gpuModel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> This property is no longer used.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a>?</code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>



category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "compute" */</code></code> The type discriminator
</summary>





</details>



</details>



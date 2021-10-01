# `Product.Compute`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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
    val version: Int?,
    val freeToUse: Boolean?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "compute" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





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

![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "compute" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>


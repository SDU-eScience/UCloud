                            [UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)
                            
                            # `Product`

                            
[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Product {
    abstract val balance: Long?
    abstract val category: ProductCategoryId
    abstract val chargeType: ChargeType
    abstract val description: String
    abstract val freeToUse: Boolean
    abstract val hiddenInGrantApplications: Boolean
    abstract val id: String
    abstract val name: String
    abstract val pricePerUnit: Long
    abstract val priority: Int
    abstract val productType: ProductType
    abstract val unitOfPrice: ProductPriceUnit
    abstract val version: Int

    class Storage : Product()
    class Compute : Product()
    class Ingress : Product()
    class License : Product()
    class NetworkIP : Product()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
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
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>


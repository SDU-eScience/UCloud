[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `ProductCategory`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProductCategory(
    val name: String,
    val provider: String,
    val productType: ProductType,
    val accountingUnit: AccountingUnit,
    val accountingFrequency: AccountingFrequency,
    val conversionTable: List<AccountingUnitConversion>?,
    val freeToUse: Boolean?,
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
<code>provider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>accountingUnit</code>: <code><code><a href='#accountingunit'>AccountingUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>accountingFrequency</code>: <code><code><a href='#accountingfrequency'>AccountingFrequency</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conversionTable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#accountingunitconversion'>AccountingUnitConversion</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product category
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>



</details>



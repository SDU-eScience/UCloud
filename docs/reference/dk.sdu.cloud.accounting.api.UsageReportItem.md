[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `UsageReportItem`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UsageReportItem(
    val isDeltaCharge: Boolean,
    val owner: WalletOwner,
    val categoryIdV2: ProductCategoryIdV2,
    val usage: Long,
    val description: ChargeDescription,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>isDeltaCharge</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='#walletowner'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>categoryIdV2</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryIdV2.md'>ProductCategoryIdV2</a></code></code>
</summary>





</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='#chargedescription'>ChargeDescription</a></code></code>
</summary>





</details>



</details>



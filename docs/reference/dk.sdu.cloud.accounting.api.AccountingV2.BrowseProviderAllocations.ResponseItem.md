[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `AccountingV2.BrowseProviderAllocations.ResponseItem`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResponseItem(
    val id: String,
    val owner: WalletOwner,
    val categoryId: ProductCategory,
    val notBefore: Long,
    val notAfter: Long,
    val quota: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='#walletowner'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md'>ProductCategory</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The earliest timestamp which allows for the balance to be consumed
</summary>





</details>

<details>
<summary>
<code>notAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The earliest timestamp at which the reported balance is no longer fully usable
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



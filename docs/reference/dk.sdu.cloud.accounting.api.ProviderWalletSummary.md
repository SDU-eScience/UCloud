[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Wallets](/docs/developer-guide/accounting-and-projects/accounting/wallets.md)

# `ProviderWalletSummary`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProviderWalletSummary(
    val id: String,
    val owner: WalletOwner,
    val categoryId: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unitOfPrice: ProductPriceUnit,
    val maxUsableBalance: Long,
    val maxPromisedBalance: Long,
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
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryId.md'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md'>ChargeType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md'>ProductPriceUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Maximum balance usable until a charge would fail
</summary>



This balance is calculated when the data is requested and thus can immediately become invalid due to changes
in the tree.


</details>

<details>
<summary>
<code>maxPromisedBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Maximum balance usable as promised by a top-level grant giver
</summary>



This balance is calculated when the data is requested and thus can immediately become invalid due to changes
in the tree.


</details>



</details>



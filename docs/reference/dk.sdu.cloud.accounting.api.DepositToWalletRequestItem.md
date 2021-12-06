[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `DepositToWalletRequestItem`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DepositToWalletRequestItem(
    val recipient: WalletOwner,
    val sourceAllocation: String,
    val amount: Long,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
    val dry: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>recipient</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The recipient of this deposit
</summary>





</details>

<details>
<summary>
<code>sourceAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the source allocation which the deposit will draw from
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The amount of credits to deposit into the recipient's wallet
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of this change. This is used purely for presentation purposes.
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become valid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
immediately.


</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become invalid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation will never
expire.


</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>

<details>
<summary>
<code>dry</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



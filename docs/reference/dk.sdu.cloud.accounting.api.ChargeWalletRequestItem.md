[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `ChargeWalletRequestItem`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ChargeWalletRequestItem(
    val payer: WalletOwner,
    val units: Long,
    val periods: Long,
    val product: ProductReference,
    val performedBy: String,
    val description: String,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>payer</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The payer of this charge
</summary>





</details>

<details>
<summary>
<code>units</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of units that this charge is about
</summary>



The unit itself is defined by the product. The unit can, for example, describe that the 'units' describe the
number of minutes/hours/days.


</details>

<details>
<summary>
<code>periods</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of products involved in this charge, for example the number of nodes
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which the service is charging for
</summary>





</details>

<details>
<summary>
<code>performedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The username of the user who generated this request
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of the charge this is used purely for presentation purposes
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



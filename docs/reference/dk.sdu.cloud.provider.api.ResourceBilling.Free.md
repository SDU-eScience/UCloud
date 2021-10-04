# `ResourceBilling.Free`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


_Contains information related to the accounting/billing of a `Resource`_

```kotlin
data class Free(
    val creditsCharged: Long,
    val pricePerUnit: Long,
)
```
Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
to be charged a different price than newly launched products.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>creditsCharged</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `WalletV2`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class WalletV2(
    val owner: WalletOwner,
    val paysFor: ProductCategory,
    val allocationGroups: List<AllocationGroupWithParent>,
    val children: List<AllocationGroupWithChild>?,
    val totalUsage: Long,
    val localUsage: Long,
    val maxUsable: Long,
    val quota: Long,
    val totalAllocated: Long,
    val lastSignificantUpdateAt: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='#walletowner'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>paysFor</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md'>ProductCategory</a></code></code>
</summary>





</details>

<details>
<summary>
<code>allocationGroups</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#allocationgroupwithparent'>AllocationGroupWithParent</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>children</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#allocationgroupwithchild'>AllocationGroupWithChild</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>totalUsage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>localUsage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>maxUsable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>totalAllocated</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>lastSignificantUpdateAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



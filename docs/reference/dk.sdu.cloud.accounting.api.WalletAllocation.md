[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Wallets](/docs/developer-guide/accounting-and-projects/accounting/wallets.md)

# `WalletAllocation`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An allocation grants access to resources_

```kotlin
data class WalletAllocation(
    val id: String,
    val allocationPath: List<String>,
    val balance: Long,
    val initialBalance: Long,
    val localBalance: Long,
    val startDate: Long,
    val endDate: Long?,
    val grantedIn: Long?,
    val canAllocate: Boolean?,
    val allowSubAllocationsToAllocate: Boolean?,
)
```
You can find more information about WalletAllocations
[here](/docs/developer-guide/accounting-and-projects/accounting/wallets.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique ID of this allocation
</summary>





</details>

<details>
<summary>
<code>allocationPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A path, starting from the top, through the allocations that will be charged, when a charge is made
</summary>



Note that this allocation path will always include, as its last element, this allocation.


</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The current balance of this wallet allocation's subtree
</summary>





</details>

<details>
<summary>
<code>initialBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The initial balance which was granted to this allocation
</summary>





</details>

<details>
<summary>
<code>localBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The current balance of this wallet allocation
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp for when this allocation becomes valid
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Timestamp for when this allocation becomes invalid, null indicates that this allocation does not expire automatically
</summary>





</details>

<details>
<summary>
<code>grantedIn</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> ID reference to which grant application this allocation was granted in
</summary>





</details>

<details>
<summary>
<code>canAllocate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A property which indicates if this allocation can be used to create sub-allocations
</summary>





</details>

<details>
<summary>
<code>allowSubAllocationsToAllocate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A property which indicates that new sub-allocations of this allocation by default should have canAllocate = true
</summary>





</details>



</details>



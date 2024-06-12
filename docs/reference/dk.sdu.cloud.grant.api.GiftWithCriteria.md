[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Gifts](/docs/developer-guide/accounting-and-projects/grants/gifts.md)

# `GiftWithCriteria`


[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Gift` along with the `criteria` for which that can `Gifts.claimGift` this_

```kotlin
data class GiftWithCriteria(
    val id: Long,
    val resourcesOwnedBy: String,
    val title: String,
    val description: String,
    val resources: List<GrantApplication.AllocationRequest>,
    val renewEvery: Int,
    val criteria: List<UserCriteria>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resourcesOwnedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the project which owns these resources
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The title of a gift
</summary>



Suitable for presentation in UIs


</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The title of a gift
</summary>



Suitable for presentation in UIs


</details>

<details>
<summary>
<code>resources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.AllocationRequest.md'>GrantApplication.AllocationRequest</a>&gt;</code></code> A list of resources which will be granted to users `Gifts.claimGift` this `Gift`.
</summary>





</details>

<details>
<summary>
<code>renewEvery</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> Renewal policy for the gift
</summary>





</details>

<details>
<summary>
<code>criteria</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.grant.api.UserCriteria.md'>UserCriteria</a>&gt;</code></code>
</summary>





</details>



</details>



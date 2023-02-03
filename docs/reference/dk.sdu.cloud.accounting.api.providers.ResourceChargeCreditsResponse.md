[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# `ResourceChargeCreditsResponse`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceChargeCreditsResponse(
    val insufficientFunds: List<FindByStringId>,
    val duplicateCharges: List<FindByStringId>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>insufficientFunds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code></code> A list of resources which could not be charged due to lack of funds. If all resources were charged successfully then this will empty.
</summary>





</details>

<details>
<summary>
<code>duplicateCharges</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code></code> A list of resources which could not be charged due to it being a duplicate charge. If all resources were charged successfully this will be empty.
</summary>





</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `GrantApplication.Status`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Status(
    val overallState: GrantApplication.State,
    val stateBreakdown: List<GrantApplication.GrantGiverApprovalState>,
    val comments: List<GrantApplication.Comment>,
    val revisions: List<GrantApplication.Revision>,
    val projectTitle: String?,
    val projectPI: String,
    val type: String /* "status" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>overallState</code>: <code><code><a href='#grantapplication.state'>GrantApplication.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>stateBreakdown</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.grantgiverapprovalstate'>GrantApplication.GrantGiverApprovalState</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>comments</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.comment'>GrantApplication.Comment</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>revisions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.revision'>GrantApplication.Revision</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>projectPI</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "status" */</code></code> The type discriminator
</summary>





</details>



</details>



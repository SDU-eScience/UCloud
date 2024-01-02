[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `GrantApplication.Document`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Document(
    val recipient: GrantApplication.Recipient,
    val allocationRequests: List<GrantApplication.AllocationRequest>,
    val form: GrantApplication.Form,
    val referenceIds: List<String>?,
    val revisionComment: String?,
    val parentProjectId: String?,
    val type: String /* "document" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>recipient</code>: <code><code><a href='#grantapplication.recipient'>GrantApplication.Recipient</a></code></code> Describes the recipient of resources, should the application be accepted
</summary>



Updateable by: Original creator (createdBy of application)
Immutable after creation: Yes


</details>

<details>
<summary>
<code>allocationRequests</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.allocationrequest'>GrantApplication.AllocationRequest</a>&gt;</code></code> Describes the allocations for resources which are requested by this application
</summary>



Updateable by: Original creator and grant givers
Immutable after creation: No


</details>

<details>
<summary>
<code>form</code>: <code><code><a href='#grantapplication.form'>GrantApplication.Form</a></code></code> A form describing why these resources are being requested
</summary>



Updateable by: Original creator
Immutable after creation: No


</details>

<details>
<summary>
<code>referenceIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> A reference used for out-of-band bookkeeping
</summary>



Updateable by: Grant givers
Immutable after creation: No


</details>

<details>
<summary>
<code>revisionComment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A comment describing why this change was made
</summary>



Update by: Original creator and grant givers
Immutable after creation: No. First revision must always be null.


</details>

<details>
<summary>
<code>parentProjectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> When creating a new project the user should choose one of the affiliations to be its parent.
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "document" */</code></code> The type discriminator
</summary>





</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `GrantApplication`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplication(
    val id: String,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val currentRevision: GrantApplication.Revision,
    val status: GrantApplication.Status,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique identifier representing a GrantApplication
</summary>



The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
closed.


</details>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Username of the user who originially submitted the application
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp representing when the application was originially submitted
</summary>





</details>

<details>
<summary>
<code>updatedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp representing when the application was last updated
</summary>





</details>

<details>
<summary>
<code>currentRevision</code>: <code><code><a href='#grantapplication.revision'>GrantApplication.Revision</a></code></code> Information about the current revision
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#grantapplication.status'>GrantApplication.Status</a></code></code> Status information about the application in its entireity
</summary>





</details>



</details>



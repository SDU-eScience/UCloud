[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `GrantApplication.Revision`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Contains information about a specific revision of the application._

```kotlin
data class Revision(
    val createdAt: Long,
    val updatedBy: String,
    val revisionNumber: Int,
    val document: GrantApplication.Document,
    val type: String /* "revision" */,
)
```
The primary contents of the revision is stored in the document. The document describes the contents of the
application, including which resource allocations are requested and by whom. Every time a change is made to
the application, a new revision is created. Each revision contains the full document. Changes between versions
can be computed by comparing with the previous revision.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp indicating when this revision was made
</summary>





</details>

<details>
<summary>
<code>updatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Username of the user who created this revision
</summary>





</details>

<details>
<summary>
<code>revisionNumber</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> A number indicating which revision this is
</summary>



Revision numbers are guaranteed to be unique and always increasing. The first revision number must be 0.
The backend does not guarantee that revision numbers are issued without gaps. Thus it is allowed for the
first revision to be 0 and the second revision to be 10.


</details>

<details>
<summary>
<code>document</code>: <code><code><a href='#grantapplication.document'>GrantApplication.Document</a></code></code> Contains the application form from the end-user
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "revision" */</code></code> The type discriminator
</summary>





</details>



</details>



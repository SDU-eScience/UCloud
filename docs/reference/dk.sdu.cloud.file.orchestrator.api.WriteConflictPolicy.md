[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `WriteConflictPolicy`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A policy for how UCloud should handle potential naming conflicts for certain operations (e.g. copy)_

```kotlin
enum class WriteConflictPolicy {
    RENAME,
    REJECT,
    REPLACE,
    MERGE_RENAME,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>RENAME</code> UCloud should handle the conflict by renaming the file
</summary>





</details>

<details>
<summary>
<code>REJECT</code> UCloud should fail the request entirely
</summary>





</details>

<details>
<summary>
<code>REPLACE</code> UCloud should replace the existing file
</summary>





</details>

<details>
<summary>
<code>MERGE_RENAME</code> "Attempt to merge the results
</summary>



This will result in the merging of folders. Concretely this means that _directory_ conflicts will be resolved by
re-using the existing directory. If there any file conflicts in the operation then this will act identical to `RENAME`.

Note: This mode is not supported for all operations.
"


</details>



</details>



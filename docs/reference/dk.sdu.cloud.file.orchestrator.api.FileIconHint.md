[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `FileIconHint`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A hint to clients about which icon should be used in user-interfaces when representing a `UFile`_

```kotlin
enum class FileIconHint {
    DIRECTORY_STAR,
    DIRECTORY_SHARES,
    DIRECTORY_TRASH,
    DIRECTORY_JOBS,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>DIRECTORY_STAR</code> A directory containing 'starred' items
</summary>





</details>

<details>
<summary>
<code>DIRECTORY_SHARES</code> A directory which contains items that are shared between users and projects
</summary>





</details>

<details>
<summary>
<code>DIRECTORY_TRASH</code> A directory which contains items that have been 'trashed'
</summary>





</details>

<details>
<summary>
<code>DIRECTORY_JOBS</code> A directory which contains items that are related to job results
</summary>





</details>



</details>



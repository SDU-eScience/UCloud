[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `FileType`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The type of a `UFile`_

```kotlin
enum class FileType {
    FILE,
    DIRECTORY,
    SOFT_LINK,
    DANGLING_METADATA,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>FILE</code> A regular file
</summary>





</details>

<details>
<summary>
<code>DIRECTORY</code> A directory of files used for organization
</summary>





</details>

<details>
<summary>
<code>SOFT_LINK</code> A soft symbolic link which points to a different file path
</summary>





</details>

<details>
<summary>
<code>DANGLING_METADATA</code> Indicates that there used to be a file with metadata here, but the file no longer exists
</summary>

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



# `FileMetadataTemplateNamespaceType`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


_Determines how the metadata template is namespaces_

```kotlin
enum class FileMetadataTemplateNamespaceType {
    COLLABORATORS,
    PER_USER,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>COLLABORATORS</code> The template is namespaced to all collaborators
</summary>



This means at most one metadata document can exist per file.


</details>

<details>
<summary>
<code>PER_USER</code> The template is namespaced to a single user
</summary>



This means that a metadata document might exist for every user who has/had access to the file.


</details>



</details>


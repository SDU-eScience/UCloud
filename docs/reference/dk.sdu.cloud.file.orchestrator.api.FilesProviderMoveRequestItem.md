# `FilesProviderMoveRequestItem`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class FilesProviderMoveRequestItem(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    val oldId: String,
    val newId: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedOldCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedNewCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>oldId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy.md'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>


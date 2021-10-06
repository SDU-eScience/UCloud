# `ActivityEvent`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ActivityEvent {
    abstract val filePath: String
    abstract val timestamp: Long
    abstract val username: String

    class Reclassify : ActivityEvent()
    class DirectoryCreated : ActivityEvent()
    class Download : ActivityEvent()
    class Copy : ActivityEvent()
    class Uploaded : ActivityEvent()
    class UpdatedAcl : ActivityEvent()
    class UpdateProjectAcl : ActivityEvent()
    class Favorite : ActivityEvent()
    class Moved : ActivityEvent()
    class Deleted : ActivityEvent()
    class SingleFileUsedByApplication : ActivityEvent()
    class AllFilesUsedByApplication : ActivityEvent()
    class SharedWith : ActivityEvent()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filePath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>


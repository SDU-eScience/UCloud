[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `UploadTemplatesRequest`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UploadTemplatesRequest(
    val personalProject: String,
    val newProject: String,
    val existingProject: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>personalProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The template provided for new grant applications when the grant requester is a personal project
</summary>





</details>

<details>
<summary>
<code>newProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The template provided for new grant applications when the grant requester is a new project
</summary>





</details>

<details>
<summary>
<code>existingProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The template provided for new grant applications when the grant requester is an existing project
</summary>





</details>



</details>



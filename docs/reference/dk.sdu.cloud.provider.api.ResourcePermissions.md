[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# `ResourcePermissions`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourcePermissions(
    val myself: List<Permission>,
    val others: List<ResourceAclEntry>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>myself</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.Permission.md'>Permission</a>&gt;</code></code> The permissions that the requesting user has access to
</summary>





</details>

<details>
<summary>
<code>others</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;?</code></code> The permissions that other users might have access to
</summary>



This value typically needs to be included through the `includeFullPermissions` flag


</details>



</details>



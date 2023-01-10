[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Grant Settings](/docs/developer-guide/accounting-and-projects/grants/grant-settings.md)

# `AutomaticApprovalSettings`


[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Settings which control if an Application should be automatically approved_

```kotlin
data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<GrantApplication.AllocationRequest>,
)
```
The `Application` will be automatically approved if the all of the following is true:
- The requesting user matches any of the criteria in `from`
- The user has only requested resources (`Application.requestedResources`) which are present in `maxResources`
- None of the resource requests exceed the numbers specified in `maxResources`

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>from</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.projects.UserCriteria.md'>UserCriteria</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>maxResources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.AllocationRequest.md'>GrantApplication.AllocationRequest</a>&gt;</code></code>
</summary>





</details>



</details>



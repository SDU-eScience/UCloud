[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# `Permission`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The UCloud permission model_

```kotlin
enum class Permission {
    READ,
    EDIT,
    ADMIN,
    PROVIDER,
}
```
This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of
standard permissions that can be applied to a resource and its associated operations.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>READ</code> Grants an entity access to all read-based operations
</summary>



Read-based operations must not alter the state of a resource. Typical examples include the `browse` and
`retrieve*` endpoints.


</details>

<details>
<summary>
<code>EDIT</code> Grants an entity access to all write-based operations
</summary>



Write-based operations are allowed to alter the state of a resource. This permission is required for most
`update*` endpoints.


</details>

<details>
<summary>
<code>ADMIN</code> Grants an entity access to special privileged operations
</summary>



This permission will allow the entity to perform any action on the resource, unless the operation
specifies otherwise. This operation is, for example, used for updating the permissions attached to a
resource.


</details>

<details>
<summary>
<code>PROVIDER</code> Grants an entity access to special privileged operations specific to a provider
</summary>





</details>



</details>



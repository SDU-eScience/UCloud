[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Core Types](/docs/developer-guide/core/types.md)

# `Role`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Represents a `SecurityPrincipal`'s system-wide role._

```kotlin
enum class Role {
    GUEST,
    USER,
    ADMIN,
    SERVICE,
    THIRD_PARTY_APP,
    PROVIDER,
    UNKNOWN,
}
```
__This is usually not used for application-specific authorization.__

Services are encouraged to implement their own authorization control, potentially
from a common library.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>GUEST</code> The security principal is an unauthenticated guest
</summary>





</details>

<details>
<summary>
<code>USER</code> The security principal is a normal end-user.
</summary>



Normal end users can also have "admin-like" privileges in certain parts of the application.


</details>

<details>
<summary>
<code>ADMIN</code> The security principal is an administrator of the system.
</summary>



Very few users should have this role.


</details>

<details>
<summary>
<code>SERVICE</code> The security principal is a first party, __trusted__, service.
</summary>





</details>

<details>
<summary>
<code>THIRD_PARTY_APP</code> The security principal is some third party application.
</summary>



This type of role is currently not used. It is reserved for potential future purposes.


</details>

<details>
<summary>
<code>PROVIDER</code>
</summary>





</details>

<details>
<summary>
<code>UNKNOWN</code> The user role is unknown.
</summary>



If the action is somewhat low-sensitivity it should be fairly safe to assume `USER`/`THIRD_PARTY_APP`
 privileges. This means no special privileges should be granted to the user.
 
 This will only happen if we are sent a token of a newer version that what we cannot parse.


</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Core Types](/docs/developer-guide/core/types.md)

# `SecurityPrincipal`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A minimal representation of a security principal._

```kotlin
data class SecurityPrincipal(
    val username: String,
    val role: Role,
    val firstName: String,
    val lastName: String,
    val uid: Long,
    val email: String?,
    val twoFactorAuthentication: Boolean?,
    val principalType: String?,
    val serviceAgreementAccepted: Boolean?,
    val organization: String?,
)
```
More information can be gathered from an auth service, using the username as a key.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The unique username of this security principal.
</summary>



This is usually suitable for display in UIs.


</details>

<details>
<summary>
<code>role</code>: <code><code><a href='#role'>Role</a></code></code> The role of the security principal
</summary>





</details>

<details>
<summary>
<code>firstName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The first name of the security principal. Can be empty.
</summary>





</details>

<details>
<summary>
<code>lastName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The last name of the security principal. Can be empty.
</summary>





</details>

<details>
<summary>
<code>uid</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> A numeric unique identifier for this principal. The username is the preferred unique identifier.
</summary>





</details>

<details>
<summary>
<code>email</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The email of the user
</summary>





</details>

<details>
<summary>
<code>twoFactorAuthentication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A boolean flag indicating if the user has 2FA enabled for their user.
</summary>



If the token does not contain this information (old tokens generated before field's introduction) then this will
be set to `true`. This is done to avoid breaking extended tokens. This behavior will should change in a
future update.

All new tokens _should_ contain this information explicitly.


</details>

<details>
<summary>
<code>principalType</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>serviceAgreementAccepted</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A boolean indicating if the service agreement has been accepted
</summary>





</details>

<details>
<summary>
<code>organization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



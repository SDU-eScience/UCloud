<p align='center'>
<a href='/docs/developer-guide/core/users/slas.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/users/avatars.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / 2FA
# 2FA

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_UCloud supports 2FA for all users using a TOTP backend._

## Rationale

UCloud, for the most part, relies on the user's organization to enforce best practices. UCloud can be configured to
require additional factors of authentication via WAYF. On top of this UCloud allows you to optionally add TOTP based
two-factor authentication.

https://cloud.sdu.dk uses this by enforcing 2FA of all users authenticated via the `password` backend.

## Table of Contents
<details>
<summary>
<a href='#example-creating-2fa-credentials'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-creating-2fa-credentials'>Creating 2FA credentials</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#answerchallenge'><code>answerChallenge</code></a></td>
<td>Answers a challenge previously issued by createCredentials</td>
</tr>
<tr>
<td><a href='#createcredentials'><code>createCredentials</code></a></td>
<td>Creates initial 2FA credentials and bootstraps a challenge for those credentials</td>
</tr>
<tr>
<td><a href='#twofactorstatus'><code>twoFactorStatus</code></a></td>
<td>Retrieves the 2FA status of the currently authenticated user</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#answerchallengerequest'><code>AnswerChallengeRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create2facredentialsresponse'><code>Create2FACredentialsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#twofactorstatusresponse'><code>TwoFactorStatusResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Creating 2FA credentials
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
TwoFactorAuthDescriptions.twoFactorStatus.call(
    Unit,
    user
).orThrow()

/*
TwoFactorStatusResponse(
    connected = false, 
)
*/
TwoFactorAuthDescriptions.createCredentials.call(
    Unit,
    user
).orThrow()

/*
Create2FACredentialsResponse(
    challengeId = "CHALLENGE ID", 
    otpAuthUri = "OTP URI", 
    qrCodeB64Data = "QR CODE BASE64 ENCODED", 
    secret = "SECRET", 
)
*/
TwoFactorAuthDescriptions.answerChallenge.call(
    AnswerChallengeRequest(
        challengeId = "CHALLENGE ID", 
        verificationCode = 999999, 
    ),
    user
).orThrow()

/*
Unit
*/
TwoFactorAuthDescriptions.twoFactorStatus.call(
    Unit,
    user
).orThrow()

/*
TwoFactorStatusResponse(
    connected = true, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/auth/2fa/status" 

# {
#     "connected": false
# }

curl -XPOST -H "Authorization: Bearer $accessToken" "$host/auth/2fa" 

# {
#     "otpAuthUri": "OTP URI",
#     "qrCodeB64Data": "QR CODE BASE64 ENCODED",
#     "secret": "SECRET",
#     "challengeId": "CHALLENGE ID"
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/auth/2fa/challenge" -d '{
    "challengeId": "CHALLENGE ID",
    "verificationCode": 999999
}'


# {
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/auth/2fa/status" 

# {
#     "connected": true
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/auth.twofactor_creating-2fa-credentials.png)

</details>



## Remote Procedure Calls

### `answerChallenge`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Answers a challenge previously issued by createCredentials_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#answerchallengerequest'>AnswerChallengeRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `createCredentials`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates initial 2FA credentials and bootstraps a challenge for those credentials_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#create2facredentialsresponse'>Create2FACredentialsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `twoFactorStatus`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves the 2FA status of the currently authenticated user_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#twofactorstatusresponse'>TwoFactorStatusResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `AnswerChallengeRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AnswerChallengeRequest(
    val challengeId: String,
    val verificationCode: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>challengeId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>verificationCode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `Create2FACredentialsResponse`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Create2FACredentialsResponse(
    val otpAuthUri: String,
    val qrCodeB64Data: String,
    val secret: String,
    val challengeId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>otpAuthUri</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>qrCodeB64Data</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>secret</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>challengeId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `TwoFactorStatusResponse`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TwoFactorStatusResponse(
    val connected: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>connected</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---


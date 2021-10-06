[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / [Authentication](/docs/developer-guide/core/users/authentication/README.md) / Providers
# Providers

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


## Table of Contents
<details>
<summary>
<a href='#remote-procedure-calls'>1. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#retrievepublickey'><code>retrievePublicKey</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#claim'><code>claim</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#generatekeypair'><code>generateKeyPair</code></a></td>
<td>Generates an RSA key pair useful for JWT signatures</td>
</tr>
<tr>
<td><a href='#refresh'><code>refresh</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#refreshasorchestrator'><code>refreshAsOrchestrator</code></a></td>
<td>Signs an access-token to be used by a UCloud service</td>
</tr>
<tr>
<td><a href='#register'><code>register</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#renew'><code>renew</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>2. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#publickeyandrefreshtoken'><code>PublicKeyAndRefreshToken</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#refreshtoken'><code>RefreshToken</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#authprovidersrefreshasproviderrequestitem'><code>AuthProvidersRefreshAsProviderRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#authprovidersregisterrequestitem'><code>AuthProvidersRegisterRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#authprovidersgeneratekeypairresponse'><code>AuthProvidersGenerateKeyPairResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#authprovidersregisterresponseitem'><code>AuthProvidersRegisterResponseItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#authprovidersretrievepublickeyresponse'><code>AuthProvidersRetrievePublicKeyResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrievePublicKey`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#authprovidersretrievepublickeyresponse'>AuthProvidersRetrievePublicKeyResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `claim`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#authprovidersregisterresponseitem'>AuthProvidersRegisterResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#publickeyandrefreshtoken'>PublicKeyAndRefreshToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `generateKeyPair`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Generates an RSA key pair useful for JWT signatures_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#authprovidersgeneratekeypairresponse'>AuthProvidersGenerateKeyPairResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Generates an RSA key pair and returns it to the client. The key pair is not stored or registered in any
way by the authentication service.


### `refresh`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#refreshtoken'>RefreshToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.auth.api.AccessToken.md'>AccessToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `refreshAsOrchestrator`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Signs an access-token to be used by a UCloud service_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#authprovidersrefreshasproviderrequestitem'>AuthProvidersRefreshAsProviderRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.auth.api.AccessToken.md'>AccessToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This RPC signs an access-token which will be used by authorized UCloud services to act as an
orchestrator of resources.


### `register`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#authprovidersregisterrequestitem'>AuthProvidersRegisterRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#authprovidersregisterresponseitem'>AuthProvidersRegisterResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `renew`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#publickeyandrefreshtoken'>PublicKeyAndRefreshToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `PublicKeyAndRefreshToken`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PublicKeyAndRefreshToken(
    val providerId: String,
    val publicKey: String,
    val refreshToken: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>publicKey</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>refreshToken</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `RefreshToken`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RefreshToken(
    val refreshToken: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>refreshToken</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `AuthProvidersRefreshAsProviderRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AuthProvidersRefreshAsProviderRequestItem(
    val providerId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `AuthProvidersRegisterRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AuthProvidersRegisterRequestItem(
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `AuthProvidersGenerateKeyPairResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AuthProvidersGenerateKeyPairResponse(
    val publicKey: String,
    val privateKey: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>publicKey</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>privateKey</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `AuthProvidersRegisterResponseItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AuthProvidersRegisterResponseItem(
    val claimToken: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>claimToken</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `AuthProvidersRetrievePublicKeyResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AuthProvidersRetrievePublicKeyResponse(
    val publicKey: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>publicKey</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---


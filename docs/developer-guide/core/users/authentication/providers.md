<p align='center'>
<a href='/docs/developer-guide/core/users/authentication/users.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/users/authentication/password-reset.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / [Authentication](/docs/developer-guide/core/users/authentication/README.md) / Provider Authentication
# Provider Authentication

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_UCloud/Core and Providers authenticate each-other using short-lived JWTs and long-lived opaque refresh-tokens._

## Communication

All communication between UCloud and a provider is done via an HTTP(S) API, certain optional endpoints use a WebSocket
API. Note that the WebSocket protocol is an extension of HTTP, as a result the WebSocket API shares the same security
aspects as the HTTP API.

TLS is strictly required for all providers running in a production environment. The provider must use a valid
certificate, which hasn't expired and signed by a commonly recognized Certificate Authority (CA). TLS for HTTPS
connections are handled internally in UCloud by OpenJDK11+. Notably, this means that TLSv1.3 is supported. We encourage
providers to follow best practices. For inspiration, Mozilla hosts an
online [SSL configuration generator](https://ssl-config.mozilla.org).
Additionally, [this document](https://github.com/ssllabs/research/wiki/SSL-and-TLS-Deployment-Best-Practices) from SSL
Labs can provide a good starting point.

Providers should treat UCloud similarly. An integration module should ensure that all certificates served by UCloud are
valid and signed by a commonly recognized CA.

For __local development purposes only__ UCloud can communicate with a __local__ provider using HTTP. It is not possible
to configure UCloud to use self-signed certificates, and as a result it is not possible to run a local provider with a
self-signed certificate + TLS. This design choice has been made to simplify the code and avoid poorly configured UCloud
deployments.

## Authentication and Authorization

UCloud _and_ the provider authenticates and authorizes all ingoing requests. Short-lived
[JSON Web Tokens (JWT)](https://jwt.io) protect these requests.

| Token | Type | Description |
|-------|------|-------------|
| `accessToken` | [JWT](https://jwt.io) | A short-lived JWT token for authenticating regular requests |
| `refreshToken` | Opaque | An opaque token, with no explicit expiration, used for requesting new `accessToken`s |

__Table:__ The two token types used in UCloud ↔ Provider authentication

Because JWTs are short-lived, every provider must renew their JWT periodically. Providers do this by using an opaque
token called the `refreshToken`. The diagram below shows how a provider can use their `refreshToken` to generate a new
`accessToken`.

<!--
# https://sequencediagram.org/
title Requesting an access-token

database Auth DB
participant UCloud
participant "Provider P" as p


p->UCloud: auth.refresh() authenticated with refreshToken
UCloud->Auth DB: validate(refreshToken)
Auth DB->UCloud: SecurityPrincipal representing P
UCloud->p: AccessToken representing P

==P can now use the access-token==

p->UCloud: jobs.control.update(...) authenticated with accessToken
UCloud->Auth DB: Fetch keypair for P
Auth DB->UCloud: Keypair
note over UCloud: Verify accessToken using keypair
UCloud->p: OK
-->

![](/backend/app-orchestrator-service/wiki/access_token_request.svg)

__Figure:__ A provider requesting a new `accessToken` using their `refreshToken`

All calls use
the [HTTP bearer authentication scheme](https://developer.mozilla.org/en-US/docs/Web/HTTP/Authentication#authentication_schemes)
. As a result the `refreshToken` will be passed in the `Authorization` header like this:

```
HTTP/1.1 POST https://cloud.sdu.dk/auth/refresh
Authorization: Bearer $refreshToken
```

The `accessToken` is passed similarly:

```
HTTP/1.1 POST https://cloud.sdu.dk/some/call
Authorization: Bearer $accessToken
```

### Internals of `accessToken`s

In this section we describe the internals of the `accessToken` and how to verify an `accessToken` from UCloud. It is
important that all providers authenticate _every_ request they receive.

The payload of an `accessToken` is follows the same schema for both UCloud and providers.

```
{
  // Token properties
  "iat": 1234,
  "exp": 5678,
  "iss": "cloud.sdu.dk",
   
  // Authorization properties
  "role": "<ROLE>", // "SERVICE" if the token authenticates UCloud. "PROVIDER" if the token authenticates a provider.

  // User metadata
  "sub": "<USERNAME>" // A unique identifier for the provider or "_UCloud" if the token authenticates UCloud.
}
```

All JWTs signed by UCloud will use the `RS256` algorithm, internally this uses `RSASSA-PKCS1-v1_5` with `SHA-256` used
for the signature. UCloud uses a unique private & public keypair for every provider. The provider receives UCloud's
public key when the `refreshToken` of the provider is issued. UCloud will generate a new keypair if the provider's
`refreshToken` is revoked.

### Verifying `accessToken`s

As a provider, you must take the following steps to verify the authenticity of an `accessToken`:

1. Verify that the `accessToken` is signed with the `RS256` algorithm (`alg` field of the JWT header)
2. Verify that the `sub` field is equal to `"_UCloud"` (Note the '\_' prefix)
3. Verify that the `iat` (issued at) field is valid by comparing to the current time
   (See [RFC7519 Section 4.1.6](https://tools.ietf.org/html/rfc7519#section-4.1.6))
3. Verify that the `exp` (expires at) field is valid by comparing to the current time
   (See [RFC7519 Section 4.1.4](https://tools.ietf.org/html/rfc7519#section-4.1.4))
4. Verify that the `iss` (issuer) field is equal to `"cloud.sdu.dk"`
5. Verify that the `role` field is equal to `SERVICE`

It is absolutely critical that JWT verification is configured correctly. For example, some JWT verifiers are known for
having too relaxed defaults, which in the worst case will skip all verification. It is important that the verifier is
configured to _only_ accept the parameters mentioned above.

## Table of Contents
<details>
<summary>
<a href='#example-a-provider-authenticating-with-ucloud/core'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-a-provider-authenticating-with-ucloud/core'>A Provider authenticating with UCloud/Core</a></td></tr>
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
<a href='#data-models'>3. Data Models</a>
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

## Example: A Provider authenticating with UCloud/Core
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>The provider has already been registered with UCloud/Core</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* 📝 Note: The tokens shown here are not representative of tokens you will see in practice */

AuthProviders.refresh.call(
    bulkRequestOf(RefreshToken(
        refreshToken = "fb69e4367ee0fe4c76a4a926394aee547a41d998", 
    )),
    provider
).orThrow()

/*
BulkResponse(
    responses = listOf(AccessToken(
        accessToken = "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo", 
    )), 
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

# 📝 Note: The tokens shown here are not representative of tokens you will see in practice

# Authenticated as provider
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/auth/providers/refresh" -d '{
    "items": [
        {
            "refreshToken": "fb69e4367ee0fe4c76a4a926394aee547a41d998"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "accessToken": "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/auth.providers_authentication.png)

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

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
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

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#publickeyandrefreshtoken'>PublicKeyAndRefreshToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `PublicKeyAndRefreshToken`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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


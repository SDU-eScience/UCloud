[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / [Authentication](/docs/developer-guide/core/users/authentication/README.md) / [Provider Authentication](/docs/developer-guide/core/users/authentication/providers.md)

# `auth.providers.generateKeyPair`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Generates an RSA key pair useful for JWT signatures_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#authprovidersgeneratekeypairresponse'>AuthProvidersGenerateKeyPairResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Generates an RSA key pair and returns it to the client. The key pair is not stored or registered in any
way by the authentication service.



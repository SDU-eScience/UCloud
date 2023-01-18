[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Public Links (Ingress)](/docs/developer-guide/orchestration/compute/providers/ingress/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/ingress/ingoing.md)

# `ingresses.provider.PROVIDERID.init`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request from the user to (potentially) initialize any resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceInitializationRequest.md'>ResourceInitializationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request is sent by the client, if the client believes that initialization of resources 
might be needed. NOTE: This request might be sent even if initialization has already taken 
place. UCloud/Core does not check if initialization has already taken place, it simply validates
the request.



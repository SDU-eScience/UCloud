[UCloud Developer Guide](/docs/developer-guide/README.md) / [Built-in Provider](/docs/developer-guide/built-in-provider/README.md) / [UCloud/Compute](/docs/developer-guide/built-in-provider/compute/README.md) / [Jobs](/docs/developer-guide/built-in-provider/compute/jobs.md)

# `jobs.provider.ucloud.retrieveProducts`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for this providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.md'>ComputeSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint responds with the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s supported by
this provider along with details for how [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  is
supported. The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s must be registered with
UCloud/Core already.



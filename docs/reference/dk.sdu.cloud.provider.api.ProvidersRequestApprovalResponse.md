[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# `ProvidersRequestApprovalResponse`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Response type used as part of the approval process_

```kotlin
sealed class ProvidersRequestApprovalResponse {
    class RequiresSignature : ProvidersRequestApprovalResponse()
    class AwaitingAdministratorApproval : ProvidersRequestApprovalResponse()
}
```



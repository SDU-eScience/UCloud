[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# `ProvidersRequestApprovalRequest`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Request type used as part of the approval process_

```kotlin
sealed class ProvidersRequestApprovalRequest {
    class Information : ProvidersRequestApprovalRequest()
    class Sign : ProvidersRequestApprovalRequest()
}
```



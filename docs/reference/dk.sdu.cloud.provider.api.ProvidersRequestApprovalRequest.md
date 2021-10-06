# `ProvidersRequestApprovalRequest`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ProvidersRequestApprovalRequest {
    class Information : ProvidersRequestApprovalRequest()
    class Sign : ProvidersRequestApprovalRequest()
}
```


# `ProvidersRequestApprovalResponse`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class ProvidersRequestApprovalResponse {
    class RequiresSignature : ProvidersRequestApprovalResponse()
    class AwaitingAdministratorApproval : ProvidersRequestApprovalResponse()
}
```


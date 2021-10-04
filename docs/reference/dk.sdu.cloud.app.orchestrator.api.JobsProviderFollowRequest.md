# `JobsProviderFollowRequest`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class JobsProviderFollowRequest {
    class Init : JobsProviderFollowRequest()
    class CancelStream : JobsProviderFollowRequest()
}
```


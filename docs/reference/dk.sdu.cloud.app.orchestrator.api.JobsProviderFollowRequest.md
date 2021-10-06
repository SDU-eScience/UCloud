# `JobsProviderFollowRequest`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A request to start/stop a follow session_

```kotlin
sealed class JobsProviderFollowRequest {
    class Init : JobsProviderFollowRequest()
    class CancelStream : JobsProviderFollowRequest()
}
```


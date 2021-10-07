[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Shells](/docs/developer-guide/orchestration/compute/providers/shells.md)

# `ShellResponse`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ShellResponse {
    class Initialized : ShellResponse()
    class Data : ShellResponse()
    class Acknowledged : ShellResponse()
}
```



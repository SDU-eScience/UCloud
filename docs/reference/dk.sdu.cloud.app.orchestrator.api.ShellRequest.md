# `ShellRequest`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class ShellRequest {
    class Initialize : ShellRequest()
    class Input : ShellRequest()
    class Resize : ShellRequest()
}
```


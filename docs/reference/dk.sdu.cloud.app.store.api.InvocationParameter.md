# `InvocationParameter`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class InvocationParameter {
    class EnvironmentVariableParameter : InvocationParameter()
    class WordInvocationParameter : InvocationParameter()
    class VariableInvocationParameter : InvocationParameter()
    class BooleanFlagParameter : InvocationParameter()
}
```


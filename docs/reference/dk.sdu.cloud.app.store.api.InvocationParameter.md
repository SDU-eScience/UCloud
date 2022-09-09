[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `InvocationParameter`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_InvocationParameters supply values to either the command-line or environment variables._

```kotlin
sealed class InvocationParameter {
    class BooleanFlagParameter : InvocationParameter()
    class EnvironmentVariableParameter : InvocationParameter()
    class VariableInvocationParameter : InvocationParameter()
    class WordInvocationParameter : InvocationParameter()
}
```
Every parameter can run in one of two contexts. They produce a value when combined with a [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md)  
and a [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md):

- __Command line argument:__ Produces zero or more arguments for the command-line
- __Environment variable:__ Produces exactly one value.

For each of the [`InvocationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.InvocationParameter.md)  types, we will describe the value(s) they produce. We will also highlight 
notable differences between CLI args and environment variables.



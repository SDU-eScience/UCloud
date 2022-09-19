[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `AppParameterValue`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An `AppParameterValue` is value which is supplied to a parameter of an `Application`._

```kotlin
sealed class AppParameterValue {
    class BlockStorage : AppParameterValue()
    class Bool : AppParameterValue()
    class File : AppParameterValue()
    class FloatingPoint : AppParameterValue()
    class Ingress : AppParameterValue()
    class Integer : AppParameterValue()
    class License : AppParameterValue()
    class Network : AppParameterValue()
    class Peer : AppParameterValue()
    class Text : AppParameterValue()
    class TextArea : AppParameterValue()
}
```
Each value type can is type-compatible with one or more `ApplicationParameter`s. The effect of a specific value depends
on its use-site, and the type of its associated parameter.

`ApplicationParameter`s have the following usage sites:

- Invocation: This affects the command line arguments passed to the software.
- Environment variables: This affects the environment variables passed to the software.
- Resources: This only affects the resources which are imported into the software environment. Not all values can be
  used as a resource.



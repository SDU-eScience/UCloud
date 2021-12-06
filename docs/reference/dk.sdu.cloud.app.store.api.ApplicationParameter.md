[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `ApplicationParameter`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An ApplicationParameter describe a single input parameter to an Application._

```kotlin
sealed class ApplicationParameter {
    abstract val defaultValue: Any?
    abstract val description: String
    abstract val name: String
    abstract val optional: Boolean
    abstract val title: String?

    class InputFile : ApplicationParameter()
    class InputDirectory : ApplicationParameter()
    class Text : ApplicationParameter()
    class TextArea : ApplicationParameter()
    class Integer : ApplicationParameter()
    class FloatingPoint : ApplicationParameter()
    class Bool : ApplicationParameter()
    class Enumeration : ApplicationParameter()
    class Peer : ApplicationParameter()
    class Ingress : ApplicationParameter()
    class LicenseServer : ApplicationParameter()
    class NetworkIP : ApplicationParameter()
}
```
All [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md)s contain metadata used for the presentation in the frontend. This metadata 
includes a title and help-text. This allows UCloud to create a rich user-interface with widgets which are easy to 
use. 

When the user requests the creation of a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), they supply a lot of 
information. This includes a reference to the [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  and a set of [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md)s. 
The user must supply a value for every mandatory [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md). Every parameter has a type 
associated with it. This type controls the set of valid [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md)s it can take.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



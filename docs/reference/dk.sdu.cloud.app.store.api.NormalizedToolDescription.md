[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md)

# `NormalizedToolDescription`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The specification of a Tool_

```kotlin
data class NormalizedToolDescription(
    val info: NameAndVersion,
    val container: String?,
    val defaultNumberOfNodes: Int,
    val defaultTimeAllocation: SimpleDuration,
    val requiredModules: List<String>,
    val authors: List<String>,
    val title: String,
    val description: String,
    val backend: ToolBackend,
    val license: String,
    val image: String?,
    val supportedProviders: List<String>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>info</code>: <code><code><a href='#nameandversion'>NameAndVersion</a></code></code> The unique name and version tuple
</summary>





</details>

<details>
<summary>
<code>container</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Deprecated, use image instead.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>defaultNumberOfNodes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The default number of nodes
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>defaultTimeAllocation</code>: <code><code><a href='#simpleduration'>SimpleDuration</a></code></code> The default time allocation to use, if none is specified.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>requiredModules</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A list of required 'modules'
</summary>



The provider decides how to interpret this value. It is intended to be used with a module system of traditional 
HPC systems.


</details>

<details>
<summary>
<code>authors</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A list of authors
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A title for this Tool used for presentation purposes
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description for this Tool used for presentation purposes
</summary>





</details>

<details>
<summary>
<code>backend</code>: <code><code><a href='#toolbackend'>ToolBackend</a></code></code> The backend to use for this Tool
</summary>





</details>

<details>
<summary>
<code>license</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A license used for this Tool. Used for presentation purposes.
</summary>





</details>

<details>
<summary>
<code>image</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The 'image' used for this Tool
</summary>



This value depends on the `backend` used for the Tool:

- `DOCKER`: The image is a container image. Typically follows the Docker format.
- `VIRTUAL_MACHINE`: The image is a reference to a base-image

It is always up to the Provider how to interpret this value. We recommend using the `supportedProviders`
property to ensure compatibility.


</details>

<details>
<summary>
<code>supportedProviders</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> A list of supported Providers
</summary>



This property determines which Providers are supported by this Tool. The backend will not allow a user to
launch an Application which uses this Tool on a provider not listed in this value.

If no providers are supplied, then this Tool will implicitly support all Providers.


</details>



</details>



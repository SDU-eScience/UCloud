# `ApplicationInvocationDescription`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class ApplicationInvocationDescription(
    val tool: ToolReference,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter>,
    val outputFileGlobs: List<String>,
    val applicationType: ApplicationType?,
    val vnc: VncDescription?,
    val web: WebDescription?,
    val container: ContainerDescription?,
    val environment: JsonObject?,
    val allowAdditionalMounts: Boolean?,
    val allowAdditionalPeers: Boolean?,
    val allowMultiNode: Boolean?,
    val fileExtensions: List<String>?,
    val licenseServers: List<String>?,
    val shouldAllowAdditionalMounts: Boolean,
    val shouldAllowAdditionalPeers: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>tool</code>: <code><code><a href='#toolreference'>ToolReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>invocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#invocationparameter'>InvocationParameter</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationparameter'>ApplicationParameter</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>outputFileGlobs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>applicationType</code>: <code><code><a href='#applicationtype'>ApplicationType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='#vncdescription'>VncDescription</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>web</code>: <code><code><a href='#webdescription'>WebDescription</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>container</code>: <code><code><a href='#containerdescription'>ContainerDescription</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>environment</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowAdditionalMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowAdditionalPeers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowMultiNode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>fileExtensions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>licenseServers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>shouldAllowAdditionalMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>shouldAllowAdditionalPeers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>


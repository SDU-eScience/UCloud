# `VariableInvocationParameter`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String?,
    val suffixGlobal: String?,
    val prefixVariable: String?,
    val suffixVariable: String?,
    val isPrefixVariablePartOfArg: Boolean?,
    val isSuffixVariablePartOfArg: Boolean?,
    val type: String /* "var" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>variableNames</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>prefixGlobal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>suffixGlobal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>prefixVariable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>suffixVariable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isPrefixVariablePartOfArg</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isSuffixVariablePartOfArg</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "var" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>


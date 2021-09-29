# `ApplicationParameter`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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


# Shells

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



## Remote Procedure Calls

### `open`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#shellrequest'>ShellRequest</a></code>|<code><a href='#shellresponse'>ShellResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `ShellRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class ShellRequest {
    class Initialize : ShellRequest()
    class Input : ShellRequest()
    class Resize : ShellRequest()
}
```



---

### `ShellRequest.Initialize`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Initialize(
    val sessionIdentifier: String,
    val cols: Int?,
    val rows: Int?,
    val type: String /* "initialize" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sessionIdentifier</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>cols</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>rows</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "initialize" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `ShellRequest.Input`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Input(
    val data: String,
    val type: String /* "input" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>data</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "input" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `ShellRequest.Resize`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Resize(
    val cols: Int,
    val rows: Int,
    val type: String /* "resize" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>cols</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rows</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "resize" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `ShellResponse`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class ShellResponse {
    class Initialized : ShellResponse()
    class Data : ShellResponse()
    class Acknowledged : ShellResponse()
}
```



---

### `ShellResponse.Acknowledged`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Acknowledged(
    val type: String /* "ack" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "ack" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `ShellResponse.Data`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Data(
    val data: String,
    val type: String /* "data" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>data</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "data" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `ShellResponse.Initialized`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Initialized(
    val type: String /* "initialize" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "initialize" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---


<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/providers/jobs/outgoing.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/providers/ips/ingoing.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / Shells
# Shells

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


## Table of Contents
<details>
<summary>
<a href='#remote-procedure-calls'>1. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#open'><code>open</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>2. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#shellrequest'><code>ShellRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#shellrequest.initialize'><code>ShellRequest.Initialize</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#shellrequest.input'><code>ShellRequest.Input</code></a></td>
<td>An event triggered when a user types any sort of input into a terminal</td>
</tr>
<tr>
<td><a href='#shellrequest.resize'><code>ShellRequest.Resize</code></a></td>
<td>An event triggered when a user resizes a terminal</td>
</tr>
<tr>
<td><a href='#shellresponse'><code>ShellResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#shellresponse.acknowledged'><code>ShellResponse.Acknowledged</code></a></td>
<td>Emitted by the provider to acknowledge a previous request</td>
</tr>
<tr>
<td><a href='#shellresponse.data'><code>ShellResponse.Data</code></a></td>
<td>Emitted by the provider when new data is available for the terminal</td>
</tr>
<tr>
<td><a href='#shellresponse.initialized'><code>ShellResponse.Initialized</code></a></td>
<td>Emitted by the provider when the terminal has been initialized</td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `open`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#shellrequest'>ShellRequest</a></code>|<code><a href='#shellresponse'>ShellResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `ShellRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ShellRequest {
    class Initialize : ShellRequest()
    class Input : ShellRequest()
    class Resize : ShellRequest()
}
```



---

### `ShellRequest.Initialize`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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





</details>



</details>



---

### `ShellRequest.Input`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An event triggered when a user types any sort of input into a terminal_

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





</details>



</details>



---

### `ShellRequest.Resize`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An event triggered when a user resizes a terminal_

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





</details>



</details>



---

### `ShellResponse`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class ShellResponse {
    class Acknowledged : ShellResponse()
    class Data : ShellResponse()
    class Initialized : ShellResponse()
}
```



---

### `ShellResponse.Acknowledged`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Emitted by the provider to acknowledge a previous request_

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





</details>



</details>



---

### `ShellResponse.Data`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Emitted by the provider when new data is available for the terminal_

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





</details>



</details>



---

### `ShellResponse.Initialized`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Emitted by the provider when the terminal has been initialized_

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





</details>



</details>



---


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `AppParameterValue.Peer`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to a separate UCloud `Job`_

```kotlin
data class Peer(
    val hostname: String,
    val jobId: String,
    val type: String /* "peer" */,
)
```
- __Compatible with:__ `ApplicationParameter.Peer`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ The `hostname`
- __Side effects:__ Configures the firewall to allow bidirectional communication between this `Job` and the peering 
  `Job`

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>hostname</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "peer" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



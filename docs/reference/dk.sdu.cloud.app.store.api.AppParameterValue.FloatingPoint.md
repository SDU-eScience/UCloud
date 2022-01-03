[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `AppParameterValue.FloatingPoint`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A floating point value_

```kotlin
data class FloatingPoint(
    val value: Double,
    val type: String /* "floating_point" */,
)
```
- __Compatible with:__ `ApplicationParameter.FloatingPoint`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The number
- __Side effects:__ None

Internally this uses a big decimal type and there are no defined limits.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "floating_point" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



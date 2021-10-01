# `AppParameterValue.Bool`


![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


_A boolean value (true or false)_

```kotlin
data class Bool(
    val value: Boolean,
    val type: String /* "boolean" */,
)
```
- __Compatible with:__ `ApplicationParameter.Bool`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ `trueValue` of `ApplicationParameter.Bool` if value is `true` otherwise `falseValue`
- __Side effects:__ None

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "boolean" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>


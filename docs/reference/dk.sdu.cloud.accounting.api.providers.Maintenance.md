[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# `Maintenance`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Maintenance(
    val description: String,
    val availability: Maintenance.Availability,
    val startsAt: Long,
    val estimatedEndsAt: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of the scheduled/ongoing maintenance.
</summary>



The text may contain any type of character, but the operator should keep in mind that this will be displayed
in a web-application. This text should be kept to only a single paragraph, but it may contain line-breaks as
needed. This text must not be blank. The Core will require that this text contains at most 4000 characters.


</details>

<details>
<summary>
<code>availability</code>: <code><code><a href='#maintenance.availability'>Maintenance.Availability</a></code></code> Describes the availability of the affected service.
</summary>





</details>

<details>
<summary>
<code>startsAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Describes when the maintenance is expected to start.
</summary>



This is an ordinary UCloud timestamp (millis since unix epoch). The timestamp can be in the future (or past).
But, the Core will enforce that the maintenance is in the "recent" past to ensure that the timestamp is not
incorrect.


</details>

<details>
<summary>
<code>estimatedEndsAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Describes when the maintenance is expected to end.
</summary>



This property is optional and can be left blank. In this case, users will not be notified about when the
maintenance is expected to end. This can be useful if a product is reaching EOL. In either case, the description
should be used to clarify the meaning of this property.

This is an ordinary UCloud timestamp (millis since unix epoch). The timestamp can be in the future (or past).
But, the Core will enforce that the maintenance is in the "recent" past to ensure that the timestamp is not
incorrect.


</details>



</details>



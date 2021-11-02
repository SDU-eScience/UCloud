[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring and Alerting](/docs/developer-guide/core/monitoring/README.md) / [Activity](/docs/developer-guide/core/monitoring/activity.md)

# `Activity.BrowseByUser.Response`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val endOfScroll: Boolean,
    val items: List<ActivityForFrontend>,
    val nextOffset: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>endOfScroll</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>items</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#activityforfrontend'>ActivityForFrontend</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>nextOffset</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



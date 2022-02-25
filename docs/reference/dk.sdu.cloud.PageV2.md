[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Core Types](/docs/developer-guide/core/types.md)

# `PageV2`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Represents a single 'page' of results_

```kotlin
data class PageV2<T>(
    val itemsPerPage: Int,
    val items: List<T>,
    val next: String?,
)
```
Every page contains the items from the current result set, along with information which allows the client to fetch
additional information.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The expected items per page, this is extracted directly from the request
</summary>





</details>

<details>
<summary>
<code>items</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;T&gt;</code></code> The items returned in this page
</summary>



NOTE: The amount of items might differ from `itemsPerPage`, even if there are more results. The only reliable way to
check if the end of results has been reached is by checking i `next == null`.


</details>

<details>
<summary>
<code>next</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The token used to fetch additional items from this result set
</summary>





</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `AppStore.RetrieveLandingPage.Response`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val carrousel: List<CarrouselItem>,
    val topPicks: List<TopPick>,
    val categories: List<ApplicationCategory>,
    val spotlight: Spotlight?,
    val newApplications: List<ApplicationSummaryWithFavorite>,
    val recentlyUpdated: List<ApplicationSummaryWithFavorite>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>carrousel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#carrouselitem'>CarrouselItem</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>topPicks</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#toppick'>TopPick</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>categories</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationcategory'>ApplicationCategory</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>spotlight</code>: <code><code><a href='#spotlight'>Spotlight</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>recentlyUpdated</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code></code>
</summary>





</details>



</details>



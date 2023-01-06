[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Files](/docs/developer-guide/orchestration/storage/providers/files/README.md) / [Ingoing API](/docs/developer-guide/orchestration/storage/providers/files/ingoing.md)

# `FilesProviderBrowseRequest`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderBrowseRequest(
    val resolvedCollection: FileCollection,
    val browse: ResourceBrowseRequest<UFileIncludeFlags>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>browse</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileIncludeFlags.md'>UFileIncludeFlags</a>&gt;</code></code>
</summary>





</details>



</details>



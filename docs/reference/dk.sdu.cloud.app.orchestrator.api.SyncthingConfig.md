[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Syncthing](/docs/developer-guide/orchestration/compute/syncthing.md)

# `SyncthingConfig`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SyncthingConfig(
    val folders: List<SyncthingConfig.Folder>,
    val devices: List<SyncthingConfig.Device>,
    val orchestratorInfo: SyncthingConfig.OrchestratorInfo?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>folders</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#syncthingconfig.folder'>SyncthingConfig.Folder</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>devices</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#syncthingconfig.device'>SyncthingConfig.Device</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>orchestratorInfo</code>: <code><code><a href='#syncthingconfig.orchestratorinfo'>SyncthingConfig.OrchestratorInfo</a>?</code></code>
</summary>





</details>



</details>



<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/license.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/providers/jobs/README.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / Syncthing
# Syncthing

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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
<td><a href='#retrieveconfiguration'><code>retrieveConfiguration</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resetconfiguration'><code>resetConfiguration</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#restart'><code>restart</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateconfiguration'><code>updateConfiguration</code></a></td>
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
<td><a href='#syncthingconfig'><code>SyncthingConfig</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#syncthingconfig.device'><code>SyncthingConfig.Device</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#syncthingconfig.folder'><code>SyncthingConfig.Folder</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#syncthingconfig.orchestratorinfo'><code>SyncthingConfig.OrchestratorInfo</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsresetconfigrequest'><code>IAppsResetConfigRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsrestartrequest'><code>IAppsRestartRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsretrieveconfigrequest'><code>IAppsRetrieveConfigRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsupdateconfigrequest'><code>IAppsUpdateConfigRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsresetconfigresponse'><code>IAppsResetConfigResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsrestartresponse'><code>IAppsRestartResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsretrieveconfigresponse'><code>IAppsRetrieveConfigResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#iappsupdateconfigresponse'><code>IAppsUpdateConfigResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrieveConfiguration`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#iappsretrieveconfigrequest'>IAppsRetrieveConfigRequest</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='#iappsretrieveconfigresponse'>IAppsRetrieveConfigResponse</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `resetConfiguration`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#iappsresetconfigrequest'>IAppsResetConfigRequest</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='#iappsresetconfigresponse'>IAppsResetConfigResponse</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `restart`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#iappsrestartrequest'>IAppsRestartRequest</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='#iappsrestartresponse'>IAppsRestartResponse</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateConfiguration`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#iappsupdateconfigrequest'>IAppsUpdateConfigRequest</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='#iappsupdateconfigresponse'>IAppsUpdateConfigResponse</a>&lt;<a href='#syncthingconfig'>SyncthingConfig</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `SyncthingConfig`

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



---

### `SyncthingConfig.Device`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Device(
    val deviceId: String,
    val label: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>deviceId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>label</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `SyncthingConfig.Folder`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Folder(
    val ucloudPath: String,
    val path: String?,
    val id: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ucloudPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `SyncthingConfig.OrchestratorInfo`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class OrchestratorInfo(
    val folderPathToPermission: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>folderPathToPermission</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

### `IAppsResetConfigRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsResetConfigRequest<ConfigType>(
    val providerId: String,
    val category: String,
    val expectedETag: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>expectedETag</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `IAppsRestartRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsRestartRequest<ConfigType>(
    val providerId: String,
    val category: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `IAppsRetrieveConfigRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsRetrieveConfigRequest<ConfigType>(
    val providerId: String,
    val category: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `IAppsUpdateConfigRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsUpdateConfigRequest<ConfigType>(
    val providerId: String,
    val category: String,
    val config: ConfigType,
    val expectedETag: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>config</code>: <code><code>ConfigType</code></code>
</summary>





</details>

<details>
<summary>
<code>expectedETag</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `IAppsResetConfigResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsResetConfigResponse<ConfigType>(
)
```

<details>
<summary>
<b>Properties</b>
</summary>



</details>



---

### `IAppsRestartResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsRestartResponse<ConfigType>(
)
```

<details>
<summary>
<b>Properties</b>
</summary>



</details>



---

### `IAppsRetrieveConfigResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsRetrieveConfigResponse<ConfigType>(
    val etag: String,
    val config: ConfigType,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>etag</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>config</code>: <code><code>ConfigType</code></code>
</summary>





</details>



</details>



---

### `IAppsUpdateConfigResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IAppsUpdateConfigResponse<ConfigType>(
)
```

<details>
<summary>
<b>Properties</b>
</summary>



</details>



---


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `ApplicationType`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The ApplicationType determines how user's interact with an Application_

```kotlin
enum class ApplicationType {
    BATCH,
    VNC,
    WEB,
}
```
- `BATCH`: A non-interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  which runs without user input
- `VNC`: An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a remote desktop interface
- `WEB`: An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a graphical web interface

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BATCH</code> A non-interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  which runs without user input
</summary>





</details>

<details>
<summary>
<code>VNC</code> An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a remote desktop interface
</summary>





</details>

<details>
<summary>
<code>WEB</code> An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a graphical web interface
</summary>





</details>



</details>



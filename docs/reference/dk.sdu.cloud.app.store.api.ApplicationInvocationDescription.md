[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `ApplicationInvocationDescription`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The specification for how to invoke an Application_

```kotlin
data class ApplicationInvocationDescription(
    val tool: ToolReference,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter>,
    val outputFileGlobs: List<String>,
    val applicationType: ApplicationType?,
    val vnc: VncDescription?,
    val web: WebDescription?,
    val ssh: SshDescription?,
    val container: ContainerDescription?,
    val environment: JsonObject?,
    val allowAdditionalMounts: Boolean?,
    val allowAdditionalPeers: Boolean?,
    val allowMultiNode: Boolean?,
    val allowPublicIp: Boolean?,
    val allowPublicLink: Boolean?,
    val fileExtensions: List<String>?,
    val licenseServers: List<String>?,
    val modules: ModulesSection?,
    val shouldAllowAdditionalMounts: Boolean,
    val shouldAllowAdditionalPeers: Boolean,
)
```
All [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)s require a `tool`. The [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md)  specify the concrete computing environment. 
With the `tool` we get the required software packages and configuration.

In this environment, we must start some software. Any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  launched with
this [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  will only run for as long as the software runs. You can specify the command-line 
invocation through the `invocation` property. Each element in this list produce zero or more arguments for the 
actual invocation. These [`InvocationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.InvocationParameter.md)s can reference the input `parameters` of the 
[`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md). In addition, you can set the `environment` variables through the same mechanism.

All [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)s have an [`ApplicationType`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationType.md)  associated with them. This `type` determines how the 
user interacts with your [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md). We support the following types:

- `BATCH`: A non-interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  which runs without user input
- `VNC`: An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a remote desktop interface
- `WEB`:  An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a graphical web interface

The [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  must expose information about how to access interactive services. It can do so by 
setting `vnc` and `web`. Providers must use this information when 
[opening an interactive session](/docs/reference/jobs.openInteractiveSession.md). 

Users can launch a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  with additional `resources`, such as 
IP addresses and files. The [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  author specifies the supported resources through the 
`allowXXX` properties.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>tool</code>: <code><code><a href='#toolreference'>ToolReference</a></code></code> A reference to the Tool used by this Application
</summary>





</details>

<details>
<summary>
<code>invocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#invocationparameter'>InvocationParameter</a>&gt;</code></code> Instructions on how to build the command-line invocation
</summary>





</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationparameter'>ApplicationParameter</a>&gt;</code></code> The input parameters used by this Application
</summary>





</details>

<details>
<summary>
<code>outputFileGlobs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>applicationType</code>: <code><code><a href='#applicationtype'>ApplicationType</a>?</code></code> The type of this Application, it determines how users will interact with the Application
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='#vncdescription'>VncDescription</a>?</code></code> Information about how to reach the VNC service
</summary>





</details>

<details>
<summary>
<code>web</code>: <code><code><a href='#webdescription'>WebDescription</a>?</code></code> Information about how to reach the web service
</summary>





</details>

<details>
<summary>
<code>ssh</code>: <code><code><a href='#sshdescription'>SshDescription</a>?</code></code> Information about how the SSH capabilities of this application
</summary>





</details>

<details>
<summary>
<code>container</code>: <code><code><a href='#containerdescription'>ContainerDescription</a>?</code></code> Hints to the container system about how the Application should be launched
</summary>





</details>

<details>
<summary>
<code>environment</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code> Additional environment variables to be added in the environment
</summary>





</details>

<details>
<summary>
<code>allowAdditionalMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for additional file mounts (default: true for interactive apps)
</summary>





</details>

<details>
<summary>
<code>allowAdditionalPeers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for connecting Jobs together (default: true)
</summary>





</details>

<details>
<summary>
<code>allowMultiNode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable multiple replicas of this Application (default: false)
</summary>





</details>

<details>
<summary>
<code>allowPublicIp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for public IP (default false)
</summary>





</details>

<details>
<summary>
<code>allowPublicLink</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for public link (default: true for web apps)
</summary>





</details>

<details>
<summary>
<code>fileExtensions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The file extensions which this Application can handle
</summary>



This list used as a suffix filter. As a result, this list should typically include the dot.


</details>

<details>
<summary>
<code>licenseServers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> Hint used by the frontend to find appropriate license servers
</summary>





</details>

<details>
<summary>
<code>modules</code>: <code><code><a href='#modulessection'>ModulesSection</a>?</code></code> A section describing integration with a module system. Currently only valid for `CONTAINER` based applications.
</summary>





</details>

<details>
<summary>
<code>shouldAllowAdditionalMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>shouldAllowAdditionalPeers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `JobSpecification`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A specification of a Job_

```kotlin
data class JobSpecification(
    val application: NameAndVersion,
    val product: ProductReference,
    val name: String?,
    val replicas: Int?,
    val allowDuplicateJob: Boolean?,
    val parameters: JsonObject?,
    val resources: List<AppParameterValue>?,
    val timeAllocation: SimpleDuration?,
    val openedFile: String?,
    val restartOnExit: Boolean?,
    val sshEnabled: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>application</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.NameAndVersion.md'>NameAndVersion</a></code></code> A reference to the application which this job should execute
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product that this job will be executed on
</summary>





</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A name for this job assigned by the user.
</summary>



The name can help a user identify why and with which parameters a job was started. This value is suitable for display in user interfaces.


</details>

<details>
<summary>
<code>replicas</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The number of replicas to start this job in
</summary>



The `resources` supplied will be mounted in every replica. Some `resources` might only be supported in an 'exclusive use' mode. This will cause the job to fail if `replicas != 1`.


</details>

<details>
<summary>
<code>allowDuplicateJob</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Allows the job to be started even when a job is running in an identical configuration
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


By default, UCloud will prevent you from accidentally starting two jobs with identical configuration. This field must be set to `true` to allow you to create two jobs with identical configuration.


</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code> Parameters which are consumed by the job
</summary>



The available parameters are defined by the `application`. This attribute is not included by default unless `includeParameters` is specified.


</details>

<details>
<summary>
<code>resources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md'>AppParameterValue</a>&gt;?</code></code> Additional resources which are made available into the job
</summary>



This attribute is not included by default unless `includeParameters` is specified. Note: Not all resources can be attached to a job. UCloud supports the following parameter types as resources:

 - `file`
 - `peer`
 - `network`
 - `block_storage`
 - `ingress`


</details>

<details>
<summary>
<code>timeAllocation</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a>?</code></code> Time allocation for the job
</summary>



This value can be `null` which signifies that the job should not (automatically) expire. Note that some providers do not support `null`. When this value is not `null` it means that the job will be terminated, regardless of result, after the duration has expired. Some providers support extended this duration via the `extend` operation.


</details>

<details>
<summary>
<code>openedFile</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An optional path to the file which the user selected with the "Open with..." feature.
</summary>

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


This value is null if the application is not launched using the "Open with..." feature. The value of this
is passed to the compute environment in a provider specific way. We encourage providers to expose this as
an environment variable named `UCLOUD_OPEN_WITH_FILE` containing the absolute path of the file (in the
current environment). Remember that this path is the _UCloud_ path to the file and not the provider's path.


</details>

<details>
<summary>
<code>restartOnExit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A flag which indicates if this job should be restarted on exit.
</summary>

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


Not all providers support this feature and the Job will be rejected if not supported. This information can
also be queried through the product support feature.

If this flag is `true` then the Job will automatically be restarted when the provider notifies the
orchestrator about process termination. It is the responsibility of the orchestrator to notify the provider
about restarts. If the restarts are triggered by the provider, then the provider must not notify the
orchestrator about the termination. The orchestrator will trigger a new `create` request in a timely manner.
The orchestrator decides when to trigger a new `create`. For example, if a process is terminating often,
then the orchestrator might decide to wait before issuing a new `create`.


</details>

<details>
<summary>
<code>sshEnabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A flag which indicates that this job should use the built-in SSH functionality of the application/provider
</summary>

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


This flag can only be true of the application itself is marked as SSH enabled. When this flag is true, 
an SSH server will be started which allows the end-user direct access to the associated compute workload.


</details>



</details>



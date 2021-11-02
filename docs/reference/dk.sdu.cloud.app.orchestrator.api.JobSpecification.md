[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `JobSpecification`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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



</details>



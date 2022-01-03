[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `JobStatus`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class JobStatus(
    val state: JobState,
    val jobParametersJson: ExportedParameters?,
    val startedAt: Long?,
    val expiresAt: Long?,
    val resolvedApplication: Application?,
    val resolvedSupport: ResolvedSupport<Product.Compute, ComputeSupport>?,
    val resolvedProduct: Product.Compute?,
)
```
The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#jobstate'>JobState</a></code></code> The current of state of the `Job`.
</summary>



This will match the latest state set in the `updates`


</details>

<details>
<summary>
<code>jobParametersJson</code>: <code><code><a href='#exportedparameters'>ExportedParameters</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>startedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Timestamp matching when the `Job` most recently transitioned to the `RUNNING` state.
</summary>



For `Job`s which suspend this might occur multiple times. This will always point to the latest pointin time it started running.


</details>

<details>
<summary>
<code>expiresAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Timestamp matching when the `Job` is set to expire.
</summary>



This is generally equal to `startedAt + timeAllocation`. Note that this field might be `null` if the `Job` has no associated deadline. For `Job`s that suspend however, this is more likely to beequal to the initial `RUNNING` state + `timeAllocation`.


</details>

<details>
<summary>
<code>resolvedApplication</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.Application.md'>Application</a>?</code></code> The resolved application referenced by `application`.
</summary>



This attribute is not included by default unless `includeApplication` is specified.


</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md'>Product.Compute</a>, <a href='#computesupport'>ComputeSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md'>Product.Compute</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



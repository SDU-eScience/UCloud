# Jobs

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



## Remote Procedure Calls

### `delete`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Request job cancellation and destruction_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This call will request the cancellation of the associated jobs. This will make sure that the jobs
are eventually stopped and resources are released. If the job is running a virtual machine, then the
virtual machine will be stopped and destroyed. Persistent storage attached to the job will not be
deleted only temporary data from the job will be deleted.

This call is asynchronous and the cancellation may not be immediately visible in the job. Progress can
be followed using the [`jobs.retrieve`](/docs/reference/jobs.retrieve.md)), [`jobs.browse`](/docs/reference/jobs.browse.md)), [`jobs.follow`](/docs/reference/jobs.follow.md)) calls.


### `retrieveUtilization`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Retrieve utilization information from cluster_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#jobsretrieveutilizationrequest'>JobsRetrieveUtilizationRequest</a></code>|<code><a href='#jobsretrieveutilizationresponse'>JobsRetrieveUtilizationResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `follow`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Follow the progress of a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#jobsfollowresponse'>JobsFollowResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `extend`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Extend the duration of one or more jobs_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobsextendrequestitem'>JobsExtendRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This will extend the duration of one or more jobs in a bulk request. Extension of a job will add to
the current deadline of a job. Note that not all providers support this features. Providers which
do not support it will have it listed in their manifest. If a provider is asked to extend a deadline
when not supported it will send back a 400 bad request.

This call makes no guarantee that all jobs are extended in a single transaction. If the provider
supports it, then all requests made against a single provider should be made in a single transaction.
Clients can determine if their extension request against a specific target was successful by checking
if the time remaining of the job has been updated.

This call will return 2XX if all jobs have successfully been extended. The job will fail with a
status code from the provider one the first extension which fails. UCloud will not attempt to extend
more jobs after the first failure.


### `suspend`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Suspend a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Suspends the job, putting it in a paused state. Not all compute backends support this operation.
For compute backends which deals with Virtual Machines this will shutdown the Virtual Machine
without deleting any data.


### `openInteractiveSession`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobsopeninteractivesessionrequestitem'>JobsOpenInteractiveSessionRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#opensessionwithprovider'>OpenSessionWithProvider</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobspecification'>JobSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `search`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#job'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `browse`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#job'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='#job'>Job</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md'>Product.Compute</a>, <a href='#computesupport'>ComputeSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `ComputeSupport`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class ComputeSupport(
    val product: ProductReference,
    val docker: ComputeSupport.Docker?,
    val virtualMachine: ComputeSupport.VirtualMachine?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>docker</code>: <code><code><a href='#computesupport.docker'>ComputeSupport.Docker</a>?</code></code> Support for `Tool`s using the `DOCKER` backend
</summary>





</details>

<details>
<summary>
<code>virtualMachine</code>: <code><code><a href='#computesupport.virtualmachine'>ComputeSupport.VirtualMachine</a>?</code></code> Support for `Tool`s using the `VIRTUAL_MACHINE` backend
</summary>





</details>



</details>



---

### `ComputeSupport.Docker`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Docker(
    val enabled: Boolean?,
    val web: Boolean?,
    val vnc: Boolean?,
    val logs: Boolean?,
    val terminal: Boolean?,
    val peers: Boolean?,
    val timeExtension: Boolean?,
    val utilization: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable this feature
</summary>



All other flags are ignored if this is `false`.


</details>

<details>
<summary>
<code>web</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive interface of `WEB` `Application`s
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive interface of `VNC` `Application`s
</summary>





</details>

<details>
<summary>
<code>logs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the log API
</summary>





</details>

<details>
<summary>
<code>terminal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive terminal API
</summary>





</details>

<details>
<summary>
<code>peers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable connection between peering `Job`s
</summary>





</details>

<details>
<summary>
<code>timeExtension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable extension of jobs
</summary>





</details>

<details>
<summary>
<code>utilization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the retrieveUtilization of jobs
</summary>





</details>



</details>



---

### `ComputeSupport.VirtualMachine`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class VirtualMachine(
    val enabled: Boolean?,
    val logs: Boolean?,
    val vnc: Boolean?,
    val terminal: Boolean?,
    val timeExtension: Boolean?,
    val suspension: Boolean?,
    val utilization: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable this feature
</summary>



All other flags are ignored if this is `false`.


</details>

<details>
<summary>
<code>logs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the log API
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the VNC API
</summary>





</details>

<details>
<summary>
<code>terminal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive terminal API
</summary>





</details>

<details>
<summary>
<code>timeExtension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable extension of jobs
</summary>





</details>

<details>
<summary>
<code>suspension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable suspension of jobs
</summary>





</details>

<details>
<summary>
<code>utilization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the retrieveUtilization of jobs
</summary>





</details>



</details>



---

### `CpuAndMemory`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class CpuAndMemory(
    val cpu: Double,
    val memory: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>cpu</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code> Number of virtual cores
</summary>



Implement as a floating to represent fractions of a virtual core. This is for example useful for Kubernetes
(and other container systems) which will allocate milli-cpus.


</details>

<details>
<summary>
<code>memory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Memory available in bytes
</summary>





</details>



</details>



---

### `ExportedParameters`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class ExportedParameters(
    val siteVersion: Int,
    val request: ExportedParametersRequest,
    val machineType: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>siteVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>request</code>: <code><code><a href='#exportedparametersrequest'>ExportedParametersRequest</a></code></code>
</summary>





</details>

<details>
<summary>
<code>machineType</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

### `InteractiveSessionType`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
enum class InteractiveSessionType {
    WEB,
    VNC,
    SHELL,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>WEB</code>
</summary>





</details>

<details>
<summary>
<code>VNC</code>
</summary>





</details>

<details>
<summary>
<code>SHELL</code>
</summary>





</details>



</details>



---

### `Job`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


_A `Job` in UCloud is the core abstraction used to describe a unit of computation._

```kotlin
data class Job(
    val id: String,
    val owner: ResourceOwner,
    val updates: List<JobUpdate>,
    val specification: JobSpecification,
    val status: JobStatus,
    val createdAt: Long,
    val output: JobOutput?,
    val permissions: ResourcePermissions?,
    val acl: List<ResourceAclEntry>?,
    val billing: ResourceBilling.Free,
    val providerGeneratedId: String?,
)
```
They provide users a way to run their computations through a workflow similar to their own workstations but scaling to
much bigger and more machines. In a simplified view, a `Job` describes the following information:

- The `Application` which the provider should/is/has run (see [app-store](/backend/app-store-service/README.md))
- The [input parameters](/backend/app-orchestrator-service/wiki/parameters.md),
  [files and other resources](/backend/app-orchestrator-service/wiki/resources.md) required by a `Job`
- A reference to the appropriate [compute infrastructure](/backend/app-orchestrator-service/wiki/products.md), this
  includes a reference to the _provider_
- The user who launched the `Job` and in which [`Project`](/backend/project-service/README.md)

A `Job` is started by a user request containing the `specification` of a `Job`. This information is verified by the UCloud
orchestrator and passed to the provider referenced by the `Job` itself. Assuming that the provider accepts this
information, the `Job` is placed in its initial state, `IN_QUEUE`. You can read more about the requirements of the
compute environment and how to launch the software
correctly [here](/backend/app-orchestrator-service/wiki/job_launch.md).

At this point, the provider has acted on this information by placing the `Job` in its own equivalent of
a [job queue](/backend/app-orchestrator-service/wiki/provider.md#job-scheduler). Once the provider realizes that
the `Job`
is running, it will contact UCloud and place the `Job` in the `RUNNING` state. This indicates to UCloud that log files
can be retrieved and that [interactive interfaces](/backend/app-orchestrator-service/wiki/interactive.md) (`VNC`/`WEB`)
are available.

Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A `Job` has
terminated successfully if no internal error occurred in UCloud and in the provider. This means that a `Job` whose
software returns with a non-zero exit code is still considered successful. A `Job` might, for example, be placed
in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
state. Any `Job` which is in a terminal state can no longer receive any updates or change its state.

At any point after the user submits the `Job`, they may request cancellation of the `Job`. This will stop the `Job`,
delete any [ephemeral resources](/backend/app-orchestrator-service/wiki/job_launch.md#ephemeral-resources) and release
any [bound resources](/backend/app-orchestrator-service/wiki/parameters.md#resources).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Unique identifier for this job.
</summary>



UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> A reference to the owner of this job
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobUpdate.md'>JobUpdate</a>&gt;</code></code> A list of status updates from the compute backend.
</summary>



The status updates tell a story of what happened with the job. This list is ordered by the timestamp in ascending order. The current state of the job will always be the last element. `updates` is guaranteed to always contain at least one element.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#jobspecification'>JobSpecification</a></code></code> The specification used to launch this job.
</summary>



This property is always available but must be explicitly requested.


</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#jobstatus'>JobStatus</a></code></code> A summary of the `Job`'s current status
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>output</code>: <code><code><a href='#joboutput'>JobOutput</a>?</code></code> Information regarding the output of this job.
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>acl</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;?</code></code>
</summary>

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)




</details>

<details>
<summary>
<code>billing</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceBilling.Free.md'>ResourceBilling.Free</a></code></code>
</summary>

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)




</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)




</details>



</details>



---

### `JobBindKind`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
enum class JobBindKind {
    BIND,
    UNBIND,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BIND</code>
</summary>





</details>

<details>
<summary>
<code>UNBIND</code>
</summary>





</details>



</details>



---

### `JobBinding`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobBinding(
    val kind: JobBindKind,
    val job: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>kind</code>: <code><code><a href='#jobbindkind'>JobBindKind</a></code></code>
</summary>





</details>

<details>
<summary>
<code>job</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `JobIncludeFlags`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobIncludeFlags(
    val filterApplication: String?,
    val filterState: JobState?,
    val includeParameters: Boolean?,
    val includeApplication: Boolean?,
    val includeProduct: Boolean?,
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterIds: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterState</code>: <code><code><a href='#jobstate'>JobState</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeParameters</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.parameters` and `specification.resources`
</summary>





</details>

<details>
<summary>
<code>includeApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedApplication`
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
</summary>





</details>

<details>
<summary>
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProviderIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the provider ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>filterIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the resource ID. The value is comma-separated.
</summary>





</details>



</details>



---

### `JobOutput`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobOutput(
    val outputFolder: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>outputFolder</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `JobSpecification`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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



---

### `JobState`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
enum class JobState {
    IN_QUEUE,
    RUNNING,
    CANCELING,
    SUCCESS,
    FAILURE,
    EXPIRED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>IN_QUEUE</code> Any job which has been submitted and not yet in a final state where the number of tasks running is less thanthe number of tasks requested
</summary>





</details>

<details>
<summary>
<code>RUNNING</code> A job where all the tasks are running
</summary>





</details>

<details>
<summary>
<code>CANCELING</code> A job which has been cancelled, either by user request or system request
</summary>





</details>

<details>
<summary>
<code>SUCCESS</code> A job which has terminated. The job terminated with no _scheduler_ error.
</summary>



Note: A job will complete successfully even if the user application exits with an unsuccessful status code.


</details>

<details>
<summary>
<code>FAILURE</code> A job which has terminated with a failure.
</summary>



Note: A job will fail _only_ if it is the scheduler's fault


</details>

<details>
<summary>
<code>EXPIRED</code> A job which has expired and was terminated as a result
</summary>





</details>



</details>



---

### `JobStatus`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


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



---

### `JobsLog`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsLog(
    val rank: Int,
    val stdout: String?,
    val stderr: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>stdout</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>stderr</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `OpenSession`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class OpenSession {
    abstract val jobId: String
    abstract val rank: Int

    class Shell : OpenSession()
    class Web : OpenSession()
    class Vnc : OpenSession()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `OpenSession.Shell`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Shell(
    val jobId: String,
    val rank: Int,
    val sessionIdentifier: String,
    val type: String /* "shell" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sessionIdentifier</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "shell" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `OpenSession.Vnc`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Vnc(
    val jobId: String,
    val rank: Int,
    val url: String,
    val password: String?,
    val type: String /* "vnc" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>url</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>password</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "vnc" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `OpenSession.Web`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Web(
    val jobId: String,
    val rank: Int,
    val redirectClientTo: String,
    val type: String /* "web" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>redirectClientTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "web" */</code></code> The type discriminator
</summary>

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `OpenSessionWithProvider`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class OpenSessionWithProvider(
    val providerDomain: String,
    val providerId: String,
    val session: OpenSession,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerDomain</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>session</code>: <code><code><a href='#opensession'>OpenSession</a></code></code>
</summary>





</details>



</details>



---

### `QueueStatus`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class QueueStatus(
    val running: Int,
    val pending: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>running</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The number of jobs running in the system
</summary>





</details>

<details>
<summary>
<code>pending</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The number of jobs waiting in the queue
</summary>





</details>



</details>



---

### `ExportedParametersRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class ExportedParametersRequest(
    val application: NameAndVersion,
    val product: ProductReference,
    val name: String?,
    val replicas: Int,
    val parameters: JsonObject,
    val resources: List<JsonObject>,
    val timeAllocation: SimpleDuration?,
    val resolvedProduct: JsonObject?,
    val resolvedApplication: JsonObject?,
    val resolvedSupport: JsonObject?,
    val allowDuplicateJob: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>application</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.NameAndVersion.md'>NameAndVersion</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>replicas</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>timeAllocation</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedApplication</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowDuplicateJob</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `JobsExtendRequestItem`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsExtendRequestItem(
    val jobId: String,
    val requestedTime: SimpleDuration,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>requestedTime</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a></code></code>
</summary>





</details>



</details>



---

### `JobsOpenInteractiveSessionRequestItem`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsOpenInteractiveSessionRequestItem(
    val id: String,
    val rank: Int,
    val sessionType: InteractiveSessionType,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sessionType</code>: <code><code><a href='#interactivesessiontype'>InteractiveSessionType</a></code></code>
</summary>





</details>



</details>



---

### `JobsRetrieveUtilizationRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsRetrieveUtilizationRequest(
    val jobId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `JobsFollowResponse`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsFollowResponse(
    val updates: List<JobUpdate>,
    val log: List<JobsLog>,
    val newStatus: JobStatus?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobUpdate.md'>JobUpdate</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>log</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobslog'>JobsLog</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>newStatus</code>: <code><code><a href='#jobstatus'>JobStatus</a>?</code></code>
</summary>





</details>



</details>



---

### `JobsRetrieveUtilizationResponse`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsRetrieveUtilizationResponse(
    val capacity: CpuAndMemory,
    val usedCapacity: CpuAndMemory,
    val queueStatus: QueueStatus,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>capacity</code>: <code><code><a href='#cpuandmemory'>CpuAndMemory</a></code></code> The total capacity of the entire compute system
</summary>





</details>

<details>
<summary>
<code>usedCapacity</code>: <code><code><a href='#cpuandmemory'>CpuAndMemory</a></code></code> The capacity currently in use, by running jobs, of the entire compute system
</summary>





</details>

<details>
<summary>
<code>queueStatus</code>: <code><code><a href='#queuestatus'>QueueStatus</a></code></code> The system of the queue
</summary>





</details>



</details>



---


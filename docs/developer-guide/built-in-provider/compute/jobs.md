# Jobs

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


## Rationale

The calls described in this section covers the API that providers of compute must implement. Not all
features of the compute API must be implemented. The individual calls and types will describe how the manifest
affects them.
            
Compute providers must answer to the calls listed below. Providers should take care to verify the bearer
token according to the TODO documentation.
            
The provider and UCloud works in tandem by sending pushing information to each other when new information
becomes available. Compute providers can push information to UCloud by using the
[`jobs.control`](#tag/jobs.control) API.

### What information does `Job` include?

The UCloud API will communicate with the provider and include a reference of the `Job` which the request is about. The
`Job` model has several optional fields which are not always included. You can see which flags are set by UCloud when
retrieving the `Job`. If you need additional data you may use [`jobs.control.retrieve`](/docs/reference/jobs.control.retrieve.md)) to fetch additional
information about the job. The flags selected below should give the provider enough information that the rest can
easily be cached locally. For example, providers can with great benefit choose to cache product and application
information.

| Flag | Included | Comment |
|------|----------|---------|
| `includeParameters` | `true` | Specifies how the user invoked the application. |
| `includeApplication` | `false` | Application information specifies the tool and application running. Can safely be cached indefinitely by name and version. |
| `includeProduct` | `false` | Product information specifies dimensions of the machine. Can safely be cached for 24 hours by name. |
| `includeUpdates` | `false` | You, the provider, will have supplied all updates but they are stored by UCloud. |
| `includeWeb` | `false` | You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
| `includeVnc` | `false` | You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
| `includeShell` | `false` |  You, the provider, will supply this information. Asking would cause UCloud to ask you back. |
            
### Accounting
            
It is up to the provider how accounting is done and if they wish to push accounting information to UCloud. 
A provider might, for example, choose to do all of the accounting on their own (including tracking who has
access). This would allow a provider to use UCloud just as an interface.
           
If a provider wishes to use UCloud for accounting then this is possible. UCloud provides an API which 
allows the provider to charge for a running compute job. The provider may call this API repeatedly to 
charge the user for their job. UCloud will respond with a payment required if the user's wallet
is out of credits. This indicates to the compute provider that the job should be terminated (since they 
no longer have credit for the job).
 
### Example: Complete example with accounting
            
| ID | UCloud | - | Provider | Call | Message |
|----|--------|---|----------|------|---------|
| [1] Request | UCloud | → | Provider | [`jobs.provider.ucloud.create`](/docs/reference/jobs.provider.ucloud.create.md)) | Start application with ID `FOO123` |
| [1] Response | UCloud | ← | Provider | [`jobs.provider.ucloud.create`](/docs/reference/jobs.provider.ucloud.create.md)) | OK |
| [2] Request | UCloud | ← | Provider | [`jobs.control.update`](/docs/reference/jobs.control.update.md)) | Proceed to `RUNNING` |
| [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 15 minutes of use |
| [4] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 15 minutes of use |
| [5] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 15 minutes of use |
| [6] Request | UCloud | → | Provider | [`jobs.provider.ucloud.terminate`](/docs/reference/jobs.provider.ucloud.terminate.md)) | Delete `FOO123` |
| [7] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 3 minutes of use |
| [8] Request | UCloud | ← | Provider | [`jobs.control.update`](/docs/reference/jobs.control.update.md)) | Proceed to `SUCCESS` |

### Example: Missing credits
            
| ID | UCloud | - | Provider | Call | Message |
|----|--------|---|----------|------|---------|
| [1] Request | UCloud | → | Provider | [`jobs.provider.ucloud.create`](/docs/reference/jobs.provider.ucloud.create.md)) | Start application with ID `FOO123` |
| [1] Response | UCloud | ← | Provider | [`jobs.provider.ucloud.create`](/docs/reference/jobs.provider.ucloud.create.md)) | OK |
| [2] Request | UCloud | ← | Provider | [`jobs.control.update`](/docs/reference/jobs.control.update.md)) | Proceed to `RUNNING` |
| [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 15 minutes of use |
| [3] Response | UCloud | → | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | 402 Payment Required |
| [4] Request | UCloud | ← | Provider | [`jobs.control.update`](/docs/reference/jobs.control.update.md)) | Proceed to `SUCCESS` with message 'Insufficient funds' |

### Example: UCloud and provider out-of-sync

| ID | UCloud | - | Provider | Call | Message |
|----|--------|---|----------|------|---------|
| [1] Request | UCloud | → | Provider | [`jobs.provider.ucloud.create`](/docs/reference/jobs.provider.ucloud.create.md)) | Start application with ID `FOO123` |
| [1] Response | UCloud | ← | Provider | [`jobs.provider.ucloud.create`](/docs/reference/jobs.provider.ucloud.create.md)) | OK |
| [2] Request | UCloud | ← | Provider | [`jobs.control.update`](/docs/reference/jobs.control.update.md)) | Proceed to `RUNNING` |
| [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 15 minutes of use |
| [3] Response | UCloud | → | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | 402 Payment Required |           
| [3] Request | UCloud | ← | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | Charge for 15 minutes of use |
| [3] Response | UCloud | → | Provider | [`jobs.control.chargeCredits`](/docs/reference/jobs.control.chargeCredits.md)) | 402 Payment Required |           
| [4] Comment | | | | | `FOO123` disappears/crashes at provider - Provider did not notice and notify UCloud automatically |
| [5] Request | UCloud | → | Provider | [`jobs.provider.ucloud.verify`](/docs/reference/jobs.provider.ucloud.verify.md)) | Verify that `FOO123` is running |
| [5] Response | UCloud | ← | Provider | [`jobs.provider.ucloud.verify`](/docs/reference/jobs.provider.ucloud.verify.md)) | OK |
| [6] Request | UCloud | → | Provider | [`jobs.control.update`](/docs/reference/jobs.control.update.md)) | Proceed `FOO123` to `FAILURE` |


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
<td><a href='#follow'><code>follow</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveutilization'><code>retrieveUtilization</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#extend'><code>extend</code></a></td>
<td>Extend the duration of a job</td>
</tr>
<tr>
<td><a href='#openinteractivesession'><code>openInteractiveSession</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#suspend'><code>suspend</code></a></td>
<td>Suspend a job</td>
</tr>
<tr>
<td><a href='#terminate'><code>terminate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#verify'><code>verify</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `follow`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest.md'>JobsProviderFollowRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowResponse.md'>JobsProviderFollowResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.md'>ComputeSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveUtilization`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsRetrieveUtilizationResponse.md'>JobsRetrieveUtilizationResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `extend`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)


_Extend the duration of a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem.md'>JobsProviderExtendRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `openInteractiveSession`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem.md'>JobsProviderOpenInteractiveSessionRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.OpenSession.md'>OpenSession</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `suspend`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)


_Suspend a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `terminate`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAclWithResource.md'>UpdatedAclWithResource</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a>&gt;&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `verify`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




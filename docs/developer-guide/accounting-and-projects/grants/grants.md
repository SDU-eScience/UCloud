<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/accounting/allocations.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/grants/gifts.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / Allocation Process
# Allocation Process

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Grants provide a way for users of UCloud to apply for resources._

## Rationale

In order for any user to use UCloud they must have credits. Credits are required for use of any compute or 
storage. There are only two ways of receiving any credits, either through an admin directly granting you the 
credits or by receiving them from a project.

Grants acts as a more user-friendly gateway to receiving resources from a project. Every
`GrantApplication` goes through the following steps:

1. User submits application to relevant project
2. All grant givers must review the application
   - User and reviewer can comment on the application
   - User and reviewer can perform edits to the application
3. Reviewer either approve or reject
4. If the `GrantApplication` was approved then resources are granted to the recipient

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
<td><a href='#browse'><code>browse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrievelogo'><code>retrieveLogo</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieverequestsettings'><code>retrieveRequestSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletecomment'><code>deleteComment</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#postcomment'><code>postComment</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrievegrantgivers'><code>retrieveGrantGivers</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#submitrevision'><code>submitRevision</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transfer'><code>transfer</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updaterequestsettings'><code>updateRequestSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updatestate'><code>updateState</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadlogo'><code>uploadLogo</code></a></td>
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
<td><a href='#grantapplication'><code>GrantApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.comment'><code>GrantApplication.Comment</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.document'><code>GrantApplication.Document</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.form'><code>GrantApplication.Form</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.form.grantgiverinitiated'><code>GrantApplication.Form.GrantGiverInitiated</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.form.plaintext'><code>GrantApplication.Form.PlainText</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.grantgiverapprovalstate'><code>GrantApplication.GrantGiverApprovalState</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.period'><code>GrantApplication.Period</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.recipient'><code>GrantApplication.Recipient</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.recipient.existingproject'><code>GrantApplication.Recipient.ExistingProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.recipient.newproject'><code>GrantApplication.Recipient.NewProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.recipient.personalworkspace'><code>GrantApplication.Recipient.PersonalWorkspace</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.revision'><code>GrantApplication.Revision</code></a></td>
<td>Contains information about a specific revision of the application.</td>
</tr>
<tr>
<td><a href='#grantapplication.state'><code>GrantApplication.State</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.status'><code>GrantApplication.Status</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplicationfilter'><code>GrantApplicationFilter</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantgiver'><code>GrantGiver</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#templates'><code>Templates</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#templates.plaintext'><code>Templates.PlainText</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usercriteria'><code>UserCriteria</code></a></td>
<td>Describes some criteria which match a user</td>
</tr>
<tr>
<td><a href='#usercriteria.anyone'><code>UserCriteria.Anyone</code></a></td>
<td>Matches any user</td>
</tr>
<tr>
<td><a href='#usercriteria.emaildomain'><code>UserCriteria.EmailDomain</code></a></td>
<td>Matches any user with an email domain equal to `domain`</td>
</tr>
<tr>
<td><a href='#usercriteria.wayforganization'><code>UserCriteria.WayfOrganization</code></a></td>
<td>Matches any user with an organization matching `org`</td>
</tr>
<tr>
<td><a href='#grantapplication.allocationrequest'><code>GrantApplication.AllocationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantrequestsettings'><code>GrantRequestSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.browse.request'><code>GrantsV2.Browse.Request</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#grantsv2.deletecomment.request'><code>GrantsV2.DeleteComment.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.postcomment.request'><code>GrantsV2.PostComment.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievegrantgivers.request'><code>GrantsV2.RetrieveGrantGivers.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievegrantgivers.request.existingapplication'><code>GrantsV2.RetrieveGrantGivers.Request.ExistingApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievegrantgivers.request.existingproject'><code>GrantsV2.RetrieveGrantGivers.Request.ExistingProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievegrantgivers.request.newproject'><code>GrantsV2.RetrieveGrantGivers.Request.NewProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievegrantgivers.request.personalworkspace'><code>GrantsV2.RetrieveGrantGivers.Request.PersonalWorkspace</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievelogo.request'><code>GrantsV2.RetrieveLogo.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.submitrevision.request'><code>GrantsV2.SubmitRevision.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.transfer.request'><code>GrantsV2.Transfer.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.updatestate.request'><code>GrantsV2.UpdateState.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsv2.retrievegrantgivers.response'><code>GrantsV2.RetrieveGrantGivers.Response</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.browse.request'>GrantsV2.Browse.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#grantapplication'>GrantApplication</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#grantapplication'>GrantApplication</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveLogo`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.retrievelogo.request'>GrantsV2.RetrieveLogo.Request</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveRequestSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#grantrequestsettings'>GrantRequestSettings</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `deleteComment`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.deletecomment.request'>GrantsV2.DeleteComment.Request</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `postComment`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.postcomment.request'>GrantsV2.PostComment.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveGrantGivers`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.retrievegrantgivers.request'>GrantsV2.RetrieveGrantGivers.Request</a></code>|<code><a href='#grantsv2.retrievegrantgivers.response'>GrantsV2.RetrieveGrantGivers.Response</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `submitRevision`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.submitrevision.request'>GrantsV2.SubmitRevision.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `transfer`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.transfer.request'>GrantsV2.Transfer.Request</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateRequestSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: USER, ADMIN, SERVICE](https://img.shields.io/static/v1?label=Auth&message=USER,+ADMIN,+SERVICE&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantrequestsettings'>GrantRequestSettings</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateState`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsv2.updatestate.request'>GrantsV2.UpdateState.Request</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadLogo`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `GrantApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplication(
    val id: String,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val currentRevision: GrantApplication.Revision,
    val status: GrantApplication.Status,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique identifier representing a GrantApplication
</summary>



The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
closed.


</details>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Username of the user who originially submitted the application
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp representing when the application was originially submitted
</summary>





</details>

<details>
<summary>
<code>updatedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp representing when the application was last updated
</summary>





</details>

<details>
<summary>
<code>currentRevision</code>: <code><code><a href='#grantapplication.revision'>GrantApplication.Revision</a></code></code> Information about the current revision
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#grantapplication.status'>GrantApplication.Status</a></code></code> Status information about the application in its entirety
</summary>





</details>



</details>



---

### `GrantApplication.Comment`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Comment(
    val id: String,
    val username: String,
    val createdAt: Long,
    val comment: String,
    val type: String /* "comment" */,
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
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "comment" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Document`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Document(
    val recipient: GrantApplication.Recipient,
    val allocationRequests: List<GrantApplication.AllocationRequest>,
    val form: GrantApplication.Form,
    val referenceIds: List<String>?,
    val revisionComment: String?,
    val parentProjectId: String?,
    val type: String /* "document" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>recipient</code>: <code><code><a href='#grantapplication.recipient'>GrantApplication.Recipient</a></code></code> Describes the recipient of resources, should the application be accepted
</summary>



Updateable by: Original creator (createdBy of application)
Immutable after creation: Yes


</details>

<details>
<summary>
<code>allocationRequests</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.allocationrequest'>GrantApplication.AllocationRequest</a>&gt;</code></code> Describes the allocations for resources which are requested by this application
</summary>



Updateable by: Original creator and grant givers
Immutable after creation: No


</details>

<details>
<summary>
<code>form</code>: <code><code><a href='#grantapplication.form'>GrantApplication.Form</a></code></code> A form describing why these resources are being requested
</summary>



Updateable by: Original creator
Immutable after creation: No


</details>

<details>
<summary>
<code>referenceIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> A reference used for out-of-band bookkeeping
</summary>



Updateable by: Grant givers
Immutable after creation: No


</details>

<details>
<summary>
<code>revisionComment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A comment describing why this change was made
</summary>



Update by: Original creator and grant givers
Immutable after creation: No. First revision must always be null.


</details>

<details>
<summary>
<code>parentProjectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> When creating a new project the user should choose one of the affiliations to be its parent.
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "document" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Form`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Form {
    abstract val type: String /* "form" */

    class GrantGiverInitiated : Form()
    class PlainText : Form()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "form" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Form.GrantGiverInitiated`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantGiverInitiated(
    val text: String,
    val subAllocator: Boolean?,
    val type: String /* "grant_giver_initiated" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>text</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subAllocator</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "grant_giver_initiated" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Form.PlainText`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PlainText(
    val text: String,
    val type: String /* "plain_text" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>text</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "plain_text" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.GrantGiverApprovalState`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantGiverApprovalState(
    val projectId: String,
    val projectTitle: String,
    val state: GrantApplication.State,
    val type: String /* "grant_giver_approval_state" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#grantapplication.state'>GrantApplication.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "grant_giver_approval_state" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Period`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Period(
    val start: Long?,
    val end: Long?,
    val type: String /* "period" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>start</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>end</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "period" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Recipient`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Recipient {
    abstract val type: String /* "recipient" */

    class ExistingProject : Recipient()
    class NewProject : Recipient()
    class PersonalWorkspace : Recipient()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "recipient" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Recipient.ExistingProject`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExistingProject(
    val id: String,
    val type: String /* "existingProject" */,
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
<code>type</code>: <code><code>String /* "existingProject" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Recipient.NewProject`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewProject(
    val title: String,
    val type: String /* "newProject" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "newProject" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Recipient.PersonalWorkspace`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PersonalWorkspace(
    val username: String,
    val type: String /* "personalWorkspace" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "personalWorkspace" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.Revision`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Contains information about a specific revision of the application._

```kotlin
data class Revision(
    val createdAt: Long,
    val updatedBy: String,
    val revisionNumber: Int,
    val document: GrantApplication.Document,
    val type: String /* "revision" */,
)
```
The primary contents of the revision is stored in the document. The document describes the contents of the
application, including which resource allocations are requested and by whom. Every time a change is made to
the application, a new revision is created. Each revision contains the full document. Changes between versions
can be computed by comparing with the previous revision.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp indicating when this revision was made
</summary>





</details>

<details>
<summary>
<code>updatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Username of the user who created this revision
</summary>





</details>

<details>
<summary>
<code>revisionNumber</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> A number indicating which revision this is
</summary>



Revision numbers are guaranteed to be unique and always increasing. The first revision number must be 0.
The backend does not guarantee that revision numbers are issued without gaps. Thus it is allowed for the
first revision to be 0 and the second revision to be 10.


</details>

<details>
<summary>
<code>document</code>: <code><code><a href='#grantapplication.document'>GrantApplication.Document</a></code></code> Contains the application form from the end-user
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "revision" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.State`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class State {
    APPROVED,
    REJECTED,
    CLOSED,
    IN_PROGRESS,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>APPROVED</code>
</summary>





</details>

<details>
<summary>
<code>REJECTED</code>
</summary>





</details>

<details>
<summary>
<code>CLOSED</code>
</summary>





</details>

<details>
<summary>
<code>IN_PROGRESS</code>
</summary>





</details>



</details>



---

### `GrantApplication.Status`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Status(
    val overallState: GrantApplication.State,
    val stateBreakdown: List<GrantApplication.GrantGiverApprovalState>,
    val comments: List<GrantApplication.Comment>,
    val revisions: List<GrantApplication.Revision>,
    val projectTitle: String?,
    val projectPI: String,
    val type: String /* "status" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>overallState</code>: <code><code><a href='#grantapplication.state'>GrantApplication.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>stateBreakdown</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.grantgiverapprovalstate'>GrantApplication.GrantGiverApprovalState</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>comments</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.comment'>GrantApplication.Comment</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>revisions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantapplication.revision'>GrantApplication.Revision</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>projectPI</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "status" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplicationFilter`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class GrantApplicationFilter {
    SHOW_ALL,
    ACTIVE,
    INACTIVE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>SHOW_ALL</code>
</summary>





</details>

<details>
<summary>
<code>ACTIVE</code>
</summary>





</details>

<details>
<summary>
<code>INACTIVE</code>
</summary>





</details>



</details>



---

### `GrantGiver`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantGiver(
    val id: String,
    val title: String,
    val description: String,
    val templates: Templates,
    val categories: List<ProductCategory>,
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
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>templates</code>: <code><code><a href='#templates'>Templates</a></code></code>
</summary>





</details>

<details>
<summary>
<code>categories</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md'>ProductCategory</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `Templates`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Templates {
    class PlainText : Templates()
}
```



---

### `Templates.PlainText`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PlainText(
    val personalProject: String,
    val newProject: String,
    val existingProject: String,
    val type: String /* "plain_text" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>personalProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The template provided for new grant applications when the grant requester is a personal project
</summary>





</details>

<details>
<summary>
<code>newProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The template provided for new grant applications when the grant requester is a new project
</summary>





</details>

<details>
<summary>
<code>existingProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The template provided for new grant applications when the grant requester is an existing project
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "plain_text" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `UserCriteria`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes some criteria which match a user_

```kotlin
sealed class UserCriteria {
    abstract val id: String?
    abstract val type: String

    class Anyone : UserCriteria()
    class EmailDomain : UserCriteria()
    class WayfOrganization : UserCriteria()
}
```
This is used in conjunction with actions that require authorization.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `UserCriteria.Anyone`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Matches any user_

```kotlin
data class Anyone(
    val id: String?,
    val type: String,
    val type: String /* "anyone" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "anyone" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `UserCriteria.EmailDomain`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Matches any user with an email domain equal to `domain`_

```kotlin
data class EmailDomain(
    val domain: String,
    val id: String?,
    val type: String,
    val type: String /* "email" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>domain</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "email" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `UserCriteria.WayfOrganization`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Matches any user with an organization matching `org`_

```kotlin
data class WayfOrganization(
    val org: String,
    val id: String?,
    val type: String,
    val type: String /* "wayf" */,
)
```
The organization is currently derived from the information we receive from WAYF.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>org</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "wayf" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantApplication.AllocationRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AllocationRequest(
    val category: String,
    val provider: String,
    val grantGiver: String,
    val balanceRequested: Long?,
    val period: GrantApplication.Period,
    val type: String /* "allocation_request" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>provider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantGiver</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balanceRequested</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>period</code>: <code><code><a href='#grantapplication.period'>GrantApplication.Period</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "allocation_request" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `GrantRequestSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantRequestSettings(
    val enabled: Boolean,
    val description: String,
    val allowRequestsFrom: List<UserCriteria>,
    val excludeRequestsFrom: List<UserCriteria>,
    val templates: Templates,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>allowRequestsFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#usercriteria'>UserCriteria</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>excludeRequestsFrom</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#usercriteria'>UserCriteria</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>templates</code>: <code><code><a href='#templates'>Templates</a></code></code>
</summary>





</details>



</details>



---

### `GrantsV2.Browse.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class Request(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val filter: GrantApplicationFilter?,
    val includeIngoingApplications: Boolean?,
    val includeOutgoingApplications: Boolean?,
)
```
Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
semantics of the call:

| Consistency | Description |
|-------------|-------------|
| `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
| `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |

The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
results to be consistent if it contains a complete view at some point in time. In practice this means that the results
must contain all the items, in the correct order and without duplicates.

If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
consistent.

The results might become inconsistent if the client either takes too long, or a service instance goes down while
fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.

---

__📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
paginate through the results.

---

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
</summary>





</details>

<details>
<summary>
<code>next</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A token requesting the next page of items
</summary>





</details>

<details>
<summary>
<code>consistency</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.PaginationRequestV2Consistency.md'>PaginationRequestV2Consistency</a>?</code></code> Controls the consistency guarantees provided by the backend
</summary>





</details>

<details>
<summary>
<code>itemsToSkip</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Items to skip ahead
</summary>





</details>

<details>
<summary>
<code>filter</code>: <code><code><a href='#grantapplicationfilter'>GrantApplicationFilter</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeIngoingApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeOutgoingApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `GrantsV2.DeleteComment.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val applicationId: String,
    val commentId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>commentId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsV2.PostComment.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val applicationId: String,
    val comment: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsV2.RetrieveGrantGivers.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Request {
    class ExistingApplication : Request()
    class ExistingProject : Request()
    class NewProject : Request()
    class PersonalWorkspace : Request()
}
```



---

### `GrantsV2.RetrieveGrantGivers.Request.ExistingApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExistingApplication(
    val id: String,
    val type: String /* "ExistingApplication" */,
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
<code>type</code>: <code><code>String /* "ExistingApplication" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `GrantsV2.RetrieveGrantGivers.Request.ExistingProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExistingProject(
    val id: String,
    val type: String /* "ExistingProject" */,
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
<code>type</code>: <code><code>String /* "ExistingProject" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `GrantsV2.RetrieveGrantGivers.Request.NewProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewProject(
    val title: String,
    val type: String /* "NewProject" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "NewProject" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `GrantsV2.RetrieveGrantGivers.Request.PersonalWorkspace`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PersonalWorkspace(
    val type: String /* "PersonalWorkspace" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "PersonalWorkspace" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `GrantsV2.RetrieveLogo.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val projectId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsV2.SubmitRevision.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val revision: GrantApplication.Document,
    val comment: String,
    val applicationId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>revision</code>: <code><code><a href='#grantapplication.document'>GrantApplication.Document</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `GrantsV2.Transfer.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val applicationId: String,
    val target: String,
    val comment: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>target</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsV2.UpdateState.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val applicationId: String,
    val newState: GrantApplication.State,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newState</code>: <code><code><a href='#grantapplication.state'>GrantApplication.State</a></code></code>
</summary>





</details>



</details>



---

### `GrantsV2.RetrieveGrantGivers.Response`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val grantGivers: List<GrantGiver>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>grantGivers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#grantgiver'>GrantGiver</a>&gt;</code></code>
</summary>





</details>



</details>



---


<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/accounting/visualization.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/grants/grant-admin.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / Allocation Process
# Allocation Process

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Grants provide a way for users of UCloud to apply for resources._

## Rationale

In order for any user to use UCloud they must have credits. Credits are required for use of any compute or 
storage. There are only two ways of receiving any credits, either through an admin directly granting you the 
credits or by receiving them from a project.

Grants acts as a more user-friendly gateway to receiving resources from a project. Every
`GrantApplication` goes through the following steps:

1. User submits application to relevant project using `Grants.submitApplication`
2. All grant approvers must review the application
   - User and reviewer can comment on the application via `GrantComments.createComment`
   - User and reviewer can perform edits to the application via `Grants.editApplication`
3. Reviewer either performs `Grants.updateApplicationState` to approve or reject
4. If the `GrantApplication` was approved then resources are granted to the `GrantApplication.recipient`

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
<td><a href='#browseaffiliationsbyresource'><code>browseAffiliationsByResource</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#browseapplications'><code>browseApplications</code></a></td>
<td>List active [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)s</td>
</tr>
<tr>
<td><a href='#browseproducts'><code>browseProducts</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#browseprojects'><code>browseProjects</code></a></td>
<td>Endpoint for users to browse projects which they can send a [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  to</td>
</tr>
<tr>
<td><a href='#retrieveaffiliations'><code>retrieveAffiliations</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewapplication'><code>viewApplication</code></a></td>
<td>Retrieves an active [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)</td>
</tr>
<tr>
<td><a href='#closeapplication'><code>closeApplication</code></a></td>
<td>Closes an existing [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)</td>
</tr>
<tr>
<td><a href='#editapplication'><code>editApplication</code></a></td>
<td>Performs an edit to an existing [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)</td>
</tr>
<tr>
<td><a href='#submitapplication'><code>submitApplication</code></a></td>
<td>Submits a [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  to a project</td>
</tr>
<tr>
<td><a href='#transferapplication'><code>transferApplication</code></a></td>
<td>Transfers allocation request to other root project</td>
</tr>
<tr>
<td><a href='#updateapplicationstate'><code>updateApplicationState</code></a></td>
<td>Approves or rejects an existing [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication..md)  If accepted by all grant givers this will trigger granting of resources to the `GrantApplication.Document.recipient`.</td>
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
<td><a href='#createapplication'><code>CreateApplication</code></a></td>
<td><i>No description</i></td>
</tr>
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
<td><a href='#projectwithtitle'><code>ProjectWithTitle</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateapplicationstate'><code>UpdateApplicationState</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#browseapplicationsrequest'><code>BrowseApplicationsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#browseprojectsrequest'><code>BrowseProjectsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#closeapplicationrequest'><code>CloseApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#editapplicationrequest'><code>EditApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplication.allocationrequest'><code>GrantApplication.AllocationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsbrowseaffiliationsbyresourcerequest'><code>GrantsBrowseAffiliationsByResourceRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#grantsbrowseaffiliationsrequest'><code>GrantsBrowseAffiliationsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#grantsbrowseproductsrequest'><code>GrantsBrowseProductsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveapplicationrequest'><code>RetrieveApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transferapplicationrequest'><code>TransferApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsbrowseproductsresponse'><code>GrantsBrowseProductsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browseAffiliationsByResource`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsbrowseaffiliationsbyresourcerequest'>GrantsBrowseAffiliationsByResourceRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#projectwithtitle'>ProjectWithTitle</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `browseApplications`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_List active [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)s_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#browseapplicationsrequest'>BrowseApplicationsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#grantapplication'>GrantApplication</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Lists active `GrantApplication`s which are relevant to a project. By using
                    [`BrowseApplicationFlags`](/docs/reference/dk.sdu.cloud.grant.api.BrowseApplicationFlags.md)  it is possible to filter on ingoing and/or outgoing.


### `browseProducts`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsbrowseproductsrequest'>GrantsBrowseProductsRequest</a></code>|<code><a href='#grantsbrowseproductsresponse'>GrantsBrowseProductsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `browseProjects`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Endpoint for users to browse projects which they can send a [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  to_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#browseprojectsrequest'>BrowseProjectsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#projectwithtitle'>ProjectWithTitle</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Concretely, this will return a list for which the user matches the criteria listed in
`ProjectApplicationSettings.allowRequestsFrom`.


### `retrieveAffiliations`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsbrowseaffiliationsrequest'>GrantsBrowseAffiliationsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#projectwithtitle'>ProjectWithTitle</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `viewApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves an active [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#retrieveapplicationrequest'>RetrieveApplicationRequest</a></code>|<code><a href='#grantapplication'>GrantApplication</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only the creator and grant reviewers are allowed to view any given [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication..md)


### `closeApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Closes an existing [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#closeapplicationrequest'>CloseApplicationRequest</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This action is identical to rejecting the [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  using `updateApplicationState` except it can be performed by the [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  creator.


### `editApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Performs an edit to an existing [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#editapplicationrequest'>EditApplicationRequest</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Both the creator and any of the grant reviewers are allowed to edit the application.


### `submitApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Submits a [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  to a project_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#createapplication'>CreateApplication</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByLongId.md'>FindByLongId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

In order for the user to submit an application they must match any criteria in
`ProjectApplicationSettings.allowRequestsFrom`. If they are not the request will fail.


### `transferApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Transfers allocation request to other root project_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#transferapplicationrequest'>TransferApplicationRequest</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateApplicationState`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Approves or rejects an existing [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication..md)  If accepted by all grant givers this will trigger granting of resources to the `GrantApplication.Document.recipient`._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#updateapplicationstate'>UpdateApplicationState</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only the grant reviewer can perform this action.



## Data Models

### `UserCriteria`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes some criteria which match a user_

```kotlin
sealed class UserCriteria {
    class Anyone : UserCriteria()
    class EmailDomain : UserCriteria()
    class WayfOrganization : UserCriteria()
}
```
This is used in conjunction with actions that require authorization.



---

### `UserCriteria.Anyone`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Matches any user_

```kotlin
data class Anyone(
    val type: String /* "anyone" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

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
<code>type</code>: <code><code>String /* "wayf" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `CreateApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CreateApplication(
    val document: GrantApplication.Document,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>document</code>: <code><code><a href='#grantapplication.document'>GrantApplication.Document</a></code></code>
</summary>





</details>



</details>



---

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
<code>status</code>: <code><code><a href='#grantapplication.status'>GrantApplication.Status</a></code></code> Status information about the application in its entireity
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
    val referenceId: String?,
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
<code>referenceId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A reference used for out-of-band book-keeping
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

### `ProjectWithTitle`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProjectWithTitle(
    val projectId: String,
    val title: String,
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
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `UpdateApplicationState`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdateApplicationState(
    val applicationId: Long,
    val newState: GrantApplication.State,
    val notify: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newState</code>: <code><code><a href='#grantapplication.state'>GrantApplication.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notify</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `BrowseApplicationsRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class BrowseApplicationsRequest(
    val filter: GrantApplicationFilter?,
    val includeIngoingApplications: Boolean?,
    val includeOutgoingApplications: Boolean?,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
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



</details>



---

### `BrowseProjectsRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class BrowseProjectsRequest(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
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



</details>



---

### `CloseApplicationRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CloseApplicationRequest(
    val applicationId: String,
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



</details>



---

### `EditApplicationRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class EditApplicationRequest(
    val applicationId: Long,
    val document: GrantApplication.Document,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>document</code>: <code><code><a href='#grantapplication.document'>GrantApplication.Document</a></code></code>
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
    val sourceAllocation: Long?,
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
<code>sourceAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
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

### `GrantsBrowseAffiliationsByResourceRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class GrantsBrowseAffiliationsByResourceRequest(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val applicationId: String,
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
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsBrowseAffiliationsRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class GrantsBrowseAffiliationsRequest(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val recipientId: String?,
    val recipientType: String?,
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
<code>recipientId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>recipientType</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `GrantsBrowseProductsRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantsBrowseProductsRequest(
    val projectId: String,
    val recipientType: String,
    val recipientId: String,
    val showHidden: Boolean?,
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
<code>recipientType</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>recipientId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>showHidden</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `RetrieveApplicationRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RetrieveApplicationRequest(
    val id: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `TransferApplicationRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TransferApplicationRequest(
    val applicationId: Long,
    val transferToProjectId: String,
    val revisionComment: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>transferToProjectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>revisionComment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsBrowseProductsResponse`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantsBrowseProductsResponse(
    val availableProducts: List<Product>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>availableProducts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>&gt;</code></code>
</summary>





</details>



</details>



---


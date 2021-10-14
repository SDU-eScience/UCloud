<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/accounting/visualization.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/grants/gifts.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / Allocation Process
# Allocation Process

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_---_

## Rationale

__📝 NOTE:__ Section being reworked (see issue #2026)

---

[Grants] provide a way for users of UCloud to apply for resources ([ResourceRequest]) for any
[GrantRecipient]

In order for any user to use UCloud they must have resources. Resources, see
[dk.sdu.cloud.accounting.api.Wallets], are required for use of any compute or storage. There are only two
ways of receiving any credits, either through an admin directly granting you the credits or by receiving
them from a project.

The [Grants] service acts as a more user-friendly gateway to receiving resources from a project. Every
[Application] goes through the following steps:

1. User submits application to relevant project using [Grants.submitApplication]
2. Project administrator of [Application.resourcesOwnedBy] reviews the application
   - User and reviewer can comment on the application via [Grants.commentOnApplication]
   - User and reviewer can perform edits to the application via [Grants.editApplication]
3. Reviewer either performs [Grants.closeApplication] or [Grants.approveApplication]
4. If the [Application] was approved then resources are granted to the [Application.grantRecipient]

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
<td><a href='#browseprojects'><code>browseProjects</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchdescription'><code>fetchDescription</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchlogo'><code>fetchLogo</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ingoingapplications'><code>ingoingApplications</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#isenabled'><code>isEnabled</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#outgoingapplications'><code>outgoingApplications</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#readrequestsettings'><code>readRequestSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#readtemplates'><code>readTemplates</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveaffiliations'><code>retrieveAffiliations</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#approveapplication'><code>approveApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#closeapplication'><code>closeApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#commentonapplication'><code>commentOnApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletecomment'><code>deleteComment</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#editapplication'><code>editApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rejectapplication'><code>rejectApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#setenabledstatus'><code>setEnabledStatus</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#submitapplication'><code>submitApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transferapplication'><code>transferApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploaddescription'><code>uploadDescription</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadlogo'><code>uploadLogo</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadrequestsettings'><code>uploadRequestSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadtemplates'><code>uploadTemplates</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewapplication'><code>viewApplication</code></a></td>
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
<td><a href='#application'><code>Application</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#applicationstatus'><code>ApplicationStatus</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#applicationwithcomments'><code>ApplicationWithComments</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#automaticapprovalsettings'><code>AutomaticApprovalSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#comment'><code>Comment</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createapplication'><code>CreateApplication</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantapplicationfilter'><code>GrantApplicationFilter</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantrecipient'><code>GrantRecipient</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantrecipient.existingproject'><code>GrantRecipient.ExistingProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantrecipient.newproject'><code>GrantRecipient.NewProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantrecipient.personalproject'><code>GrantRecipient.PersonalProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#projectapplicationsettings'><code>ProjectApplicationSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#projectwithtitle'><code>ProjectWithTitle</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usercriteria'><code>UserCriteria</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usercriteria.anyone'><code>UserCriteria.Anyone</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usercriteria.emaildomain'><code>UserCriteria.EmailDomain</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usercriteria.wayforganization'><code>UserCriteria.WayfOrganization</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#approveapplicationrequest'><code>ApproveApplicationRequest</code></a></td>
<td><i>No description</i></td>
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
<td><a href='#commentonapplicationrequest'><code>CommentOnApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletecommentrequest'><code>DeleteCommentRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#editapplicationrequest'><code>EditApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchdescriptionrequest'><code>FetchDescriptionRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchlogorequest'><code>FetchLogoRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsretrieveaffiliationsrequest'><code>GrantsRetrieveAffiliationsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#grantsretrieveproductsrequest'><code>GrantsRetrieveProductsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ingoingapplicationsrequest'><code>IngoingApplicationsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#isenabledrequest'><code>IsEnabledRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#outgoingapplicationsrequest'><code>OutgoingApplicationsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#readrequestsettingsrequest'><code>ReadRequestSettingsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#readtemplatesrequest'><code>ReadTemplatesRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rejectapplicationrequest'><code>RejectApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourcerequest'><code>ResourceRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#setenabledstatusrequest'><code>SetEnabledStatusRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transferapplicationrequest'><code>TransferApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploaddescriptionrequest'><code>UploadDescriptionRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadlogorequest'><code>UploadLogoRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadtemplatesrequest'><code>UploadTemplatesRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewapplicationrequest'><code>ViewApplicationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchdescriptionresponse'><code>FetchDescriptionResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#grantsretrieveproductsresponse'><code>GrantsRetrieveProductsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#isenabledresponse'><code>IsEnabledResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browseProjects`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#browseprojectsrequest'>BrowseProjectsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#projectwithtitle'>ProjectWithTitle</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `fetchDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#fetchdescriptionrequest'>FetchDescriptionRequest</a></code>|<code><a href='#fetchdescriptionresponse'>FetchDescriptionResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `fetchLogo`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#fetchlogorequest'>FetchLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `ingoingApplications`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#ingoingapplicationsrequest'>IngoingApplicationsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#application'>Application</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `isEnabled`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#isenabledrequest'>IsEnabledRequest</a></code>|<code><a href='#isenabledresponse'>IsEnabledResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `outgoingApplications`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#outgoingapplicationsrequest'>OutgoingApplicationsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#application'>Application</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `readRequestSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#readrequestsettingsrequest'>ReadRequestSettingsRequest</a></code>|<code><a href='#projectapplicationsettings'>ProjectApplicationSettings</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `readTemplates`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#readtemplatesrequest'>ReadTemplatesRequest</a></code>|<code><a href='#uploadtemplatesrequest'>UploadTemplatesRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveAffiliations`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsretrieveaffiliationsrequest'>GrantsRetrieveAffiliationsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#projectwithtitle'>ProjectWithTitle</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#grantsretrieveproductsrequest'>GrantsRetrieveProductsRequest</a></code>|<code><a href='#grantsretrieveproductsresponse'>GrantsRetrieveProductsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `approveApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#approveapplicationrequest'>ApproveApplicationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `closeApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#closeapplicationrequest'>CloseApplicationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `commentOnApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#commentonapplicationrequest'>CommentOnApplicationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `deleteComment`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#deletecommentrequest'>DeleteCommentRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `editApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#editapplicationrequest'>EditApplicationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rejectApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#rejectapplicationrequest'>RejectApplicationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `setEnabledStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#setenabledstatusrequest'>SetEnabledStatusRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `submitApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#createapplication'>CreateApplication</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByLongId.md'>FindByLongId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `transferApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#transferapplicationrequest'>TransferApplicationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#uploaddescriptionrequest'>UploadDescriptionRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadLogo`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#uploadlogorequest'>UploadLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadRequestSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#projectapplicationsettings'>ProjectApplicationSettings</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadTemplates`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#uploadtemplatesrequest'>UploadTemplatesRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `viewApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#viewapplicationrequest'>ViewApplicationRequest</a></code>|<code><a href='#applicationwithcomments'>ApplicationWithComments</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Application`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Application(
    val status: ApplicationStatus,
    val resourcesOwnedBy: String,
    val requestedBy: String,
    val grantRecipient: GrantRecipient,
    val document: String,
    val requestedResources: List<ResourceRequest>,
    val id: Long,
    val resourcesOwnedByTitle: String,
    val grantRecipientPi: String,
    val grantRecipientTitle: String,
    val createdAt: Long,
    val updatedAt: Long,
    val statusChangedBy: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>status</code>: <code><code><a href='#applicationstatus'>ApplicationStatus</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resourcesOwnedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>requestedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantRecipient</code>: <code><code><a href='#grantrecipient'>GrantRecipient</a></code></code>
</summary>





</details>

<details>
<summary>
<code>document</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>requestedResources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#resourcerequest'>ResourceRequest</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resourcesOwnedByTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantRecipientPi</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantRecipientTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>updatedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>statusChangedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ApplicationStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ApplicationStatus {
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

### `ApplicationWithComments`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ApplicationWithComments(
    val application: Application,
    val comments: List<Comment>,
    val approver: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>application</code>: <code><code><a href='#application'>Application</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comments</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#comment'>Comment</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>approver</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `AutomaticApprovalSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AutomaticApprovalSettings(
    val from: List<UserCriteria>,
    val maxResources: List<ResourceRequest>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>from</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#usercriteria'>UserCriteria</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>maxResources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#resourcerequest'>ResourceRequest</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `Comment`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Comment(
    val id: Long,
    val postedBy: String,
    val postedAt: Long,
    val comment: String,
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

<details>
<summary>
<code>postedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>postedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `CreateApplication`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CreateApplication(
    val resourcesOwnedBy: String,
    val grantRecipient: GrantRecipient,
    val document: String,
    val requestedResources: List<ResourceRequest>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resourcesOwnedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantRecipient</code>: <code><code><a href='#grantrecipient'>GrantRecipient</a></code></code>
</summary>





</details>

<details>
<summary>
<code>document</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>requestedResources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#resourcerequest'>ResourceRequest</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `GrantApplicationFilter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

### `GrantRecipient`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class GrantRecipient {
    class PersonalProject : GrantRecipient()
    class ExistingProject : GrantRecipient()
    class NewProject : GrantRecipient()
}
```



---

### `GrantRecipient.ExistingProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExistingProject(
    val projectId: String,
    val type: String /* "existing_project" */,
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
<code>type</code>: <code><code>String /* "existing_project" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `GrantRecipient.NewProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewProject(
    val projectTitle: String,
    val type: String /* "new_project" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "new_project" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `GrantRecipient.PersonalProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PersonalProject(
    val username: String,
    val type: String /* "personal" */,
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
<code>type</code>: <code><code>String /* "personal" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ProjectApplicationSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProjectApplicationSettings(
    val automaticApproval: AutomaticApprovalSettings,
    val allowRequestsFrom: List<UserCriteria>,
    val excludeRequestsFrom: List<UserCriteria>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>automaticApproval</code>: <code><code><a href='#automaticapprovalsettings'>AutomaticApprovalSettings</a></code></code>
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



</details>



---

### `ProjectWithTitle`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

### `UserCriteria`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class UserCriteria {
    class Anyone : UserCriteria()
    class EmailDomain : UserCriteria()
    class WayfOrganization : UserCriteria()
}
```



---

### `UserCriteria.Anyone`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `UserCriteria.EmailDomain`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `UserCriteria.WayfOrganization`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class WayfOrganization(
    val org: String,
    val type: String /* "wayf" */,
)
```

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

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApproveApplicationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ApproveApplicationRequest(
    val requestId: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>requestId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `BrowseProjectsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CloseApplicationRequest(
    val requestId: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>requestId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `CommentOnApplicationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CommentOnApplicationRequest(
    val requestId: Long,
    val comment: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>requestId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>comment</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `DeleteCommentRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DeleteCommentRequest(
    val commentId: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>commentId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `EditApplicationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class EditApplicationRequest(
    val id: Long,
    val newDocument: String,
    val newResources: List<ResourceRequest>,
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

<details>
<summary>
<code>newDocument</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newResources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#resourcerequest'>ResourceRequest</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `FetchDescriptionRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FetchDescriptionRequest(
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

### `FetchLogoRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FetchLogoRequest(
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

### `GrantsRetrieveAffiliationsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class GrantsRetrieveAffiliationsRequest(
    val grantId: Long,
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
<code>grantId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
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

### `GrantsRetrieveProductsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantsRetrieveProductsRequest(
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

### `IngoingApplicationsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class IngoingApplicationsRequest(
    val filter: GrantApplicationFilter?,
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

### `IsEnabledRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IsEnabledRequest(
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

### `OutgoingApplicationsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class OutgoingApplicationsRequest(
    val filter: GrantApplicationFilter?,
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

### `ReadRequestSettingsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ReadRequestSettingsRequest(
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

### `ReadTemplatesRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ReadTemplatesRequest(
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

### `RejectApplicationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RejectApplicationRequest(
    val requestId: Long,
    val notify: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>requestId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notify</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ResourceRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceRequest(
    val productCategory: String,
    val productProvider: String,
    val balanceRequested: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>productCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balanceRequested</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>



</details>



---

### `SetEnabledStatusRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SetEnabledStatusRequest(
    val projectId: String,
    val enabledStatus: Boolean,
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
<code>enabledStatus</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `TransferApplicationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TransferApplicationRequest(
    val applicationId: Long,
    val transferToProjectId: String,
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



</details>



---

### `UploadDescriptionRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UploadDescriptionRequest(
    val projectId: String,
    val description: String,
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
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `UploadLogoRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UploadLogoRequest(
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

### `UploadTemplatesRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UploadTemplatesRequest(
    val personalProject: String,
    val newProject: String,
    val existingProject: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>personalProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>existingProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ViewApplicationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ViewApplicationRequest(
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

### `FetchDescriptionResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FetchDescriptionResponse(
    val description: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `GrantsRetrieveProductsResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantsRetrieveProductsResponse(
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

### `IsEnabledResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IsEnabledResponse(
    val enabled: Boolean,
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



</details>



---


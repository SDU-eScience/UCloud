<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/projects/members.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Projects](/docs/developer-guide/accounting-and-projects/projects/README.md) / Projects
# Projects

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_The projects feature allow for collaboration between different users across the entire UCloud platform._

## Rationale

This project establishes the core abstractions for projects and establishes an event stream for receiving updates about
changes. Other services extend the projects feature and subscribe to these changes to create the full project feature.

---
    
__⚠️ WARNING:__ The API listed on this page will likely change to conform with our
[API conventions](/docs/developer-guide/core/api-conventions.md). Be careful when building integrations. The following
changes are expected:

- RPC names will change to conform with the conventions
- RPC request and response types will change to conform with the conventions
- RPCs which return a page will be collapsed into a single `browse` endpoint
- Some property names will change to be consistent with [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)s

---

## Definition

A project in UCloud is a collection of `members` which is uniquely identified by an `id`. All `members` are
[users](../auth-service/README.md) identified by their `username` and have exactly one `role`. A user always has exactly one
`role`. Each project has exactly one principal investigator (`PI`). The `PI` is responsible for managing the project,
including adding and removing users.

| Role           | Notes                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------|
| `PI`           | The primary point of contact for projects. All projects have exactly one PI.                       |
| `ADMIN`        | Administrators are allowed to perform some project management. A project can have multiple admins. |
| `USER`         | Has no special privileges.                                                                         |

**Table:** The possible roles of a project, and their privileges within project
management.

A project can be updated by adding/removing/changing any of its `members`. Such an update will trigger a new message
on the event stream.

A project is sub-divided into groups:

![](/backend/accounting-service/wiki/structure.png)

Each project may have 0 or more groups. The groups can have 0 or more members. A group belongs to exactly one project,
and the members of a group can only be from the project it belongs to.

## Creating Projects and Sub-Projects

All projects create by end-users have exactly one parent project. Only UCloud administrators can create root-level
projects, that is a project without a parent. This allows users of UCloud to create a hierarchy of projects. The
project hierarchy plays a significant role in accounting.

Normal users can create a project through the [grant application](../grant-service/README.md) feature.

A project can be uniquely identified by the path from the root project to the leaf-project. As a result, the `title` of
a project must be unique within a single project. `title`s are case-insensitive.

Permissions and memberships are _not_ hierarchical. This means that a user must be explicitly added to every project
they need permissions in. UCloud administrators can always create a sub-project in any given project. A setting exists
for every project which allows normal users to create sub-projects.

---

__Example:__ A project hierarchy

![](/backend/accounting-service/wiki/subprojects.png)

__Figure 1:__ A storage hierarchy

Figure 1 shows a hierarchy of projects. Note that users deep in the hierarchy are not necessarily members of the
projects further up in the hierarchy. For example, being a member of "IMADA" does not imply membership of "NAT".
A member of "IMADA" can be a member of "NAT" but they must be _explicitly_ added to both projects.

None of the projects share _any_ resources. Each individual project will have their own home directory. The
administrators, or any other user, of "NAT" will not be able to read/write any files of "IMADA" unless they have
explicitly been added to the "IMADA" project.

## The Project Context

All requests in UCloud are executed in a particular context. The header of every request defines the context. For the
HTTP backend this is done in the `Project` header. The absence of a project implies that the request is executed in the
personal project context.

![](/backend/accounting-service/wiki/context-switcher.png)

__Figure 2:__ The UCloud user interface allows you to select context through a dropdown in the navigation header.

---

__Example:__ Accessing the project context from a microservice

```kotlin
implement(Descriptions.call) {
    val project: String? = ctx.project // null implies the personal project
    ok(service.doSomething(project))
}
```

---

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
<td><a href='#allowsrenaming'><code>allowsRenaming</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#allowssubprojectrenaming'><code>allowsSubProjectRenaming</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#countsubprojects'><code>countSubProjects</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchdatamanagementplan'><code>fetchDataManagementPlan</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listfavoriteprojects'><code>listFavoriteProjects</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listprojects'><code>listProjects</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupbyid'><code>lookupById</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupbyidbulk'><code>lookupByIdBulk</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupbypath'><code>lookupByPath</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupprincipalinvestigator'><code>lookupPrincipalInvestigator</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewancestors'><code>viewAncestors</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewmemberinproject'><code>viewMemberInProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#acceptinvite'><code>acceptInvite</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#archive'><code>archive</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#archivebulk'><code>archiveBulk</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#changeuserrole'><code>changeUserRole</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletemember'><code>deleteMember</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exists'><code>exists</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#invite'><code>invite</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#leaveproject'><code>leaveProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listingoinginvites'><code>listIngoingInvites</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listoutgoinginvites'><code>listOutgoingInvites</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listsubprojects'><code>listSubProjects</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rejectinvite'><code>rejectInvite</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rename'><code>rename</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#togglerenaming'><code>toggleRenaming</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transferpirole'><code>transferPiRole</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updatedatamanagementplan'><code>updateDataManagementPlan</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#verifymembership'><code>verifyMembership</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewproject'><code>viewProject</code></a></td>
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
<td><a href='#ingoinginvite'><code>IngoingInvite</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#memberinproject'><code>MemberInProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#outgoinginvite'><code>OutgoingInvite</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#project'><code>Project</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#projectmember'><code>ProjectMember</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#projectrole'><code>ProjectRole</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#userprojectsummary'><code>UserProjectSummary</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#acceptinviterequest'><code>AcceptInviteRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#allowsrenamingrequest'><code>AllowsRenamingRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#archivebulkrequest'><code>ArchiveBulkRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#archiverequest'><code>ArchiveRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#changeuserrolerequest'><code>ChangeUserRoleRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createprojectrequest'><code>CreateProjectRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletememberrequest'><code>DeleteMemberRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#existsrequest'><code>ExistsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#inviterequest'><code>InviteRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listfavoriteprojectsrequest'><code>ListFavoriteProjectsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listingoinginvitesrequest'><code>ListIngoingInvitesRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listoutgoinginvitesrequest'><code>ListOutgoingInvitesRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listprojectsrequest'><code>ListProjectsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listsubprojectsrequest'><code>ListSubProjectsRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#lookupbyidbulkrequest'><code>LookupByIdBulkRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupbyidrequest'><code>LookupByIdRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupbytitlerequest'><code>LookupByTitleRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#projectsearchbypathrequest'><code>ProjectSearchByPathRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#rejectinviterequest'><code>RejectInviteRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#renameprojectrequest'><code>RenameProjectRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#togglerenamingrequest'><code>ToggleRenamingRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transferpirolerequest'><code>TransferPiRoleRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updatedatamanagementplanrequest'><code>UpdateDataManagementPlanRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewmemberinprojectrequest'><code>ViewMemberInProjectRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewprojectrequest'><code>ViewProjectRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#allowsrenamingresponse'><code>AllowsRenamingResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#existsresponse'><code>ExistsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchdatamanagementplanresponse'><code>FetchDataManagementPlanResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupprincipalinvestigatorresponse'><code>LookupPrincipalInvestigatorResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#viewmemberinprojectresponse'><code>ViewMemberInProjectResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `allowsRenaming`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#allowsrenamingrequest'>AllowsRenamingRequest</a></code>|<code><a href='#allowsrenamingresponse'>AllowsRenamingResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `allowsSubProjectRenaming`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#allowsrenamingrequest'>AllowsRenamingRequest</a></code>|<code><a href='#allowsrenamingresponse'>AllowsRenamingResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `countSubProjects`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `fetchDataManagementPlan`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#fetchdatamanagementplanresponse'>FetchDataManagementPlanResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listFavoriteProjects`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listfavoriteprojectsrequest'>ListFavoriteProjectsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#userprojectsummary'>UserProjectSummary</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listProjects`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listprojectsrequest'>ListProjectsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#userprojectsummary'>UserProjectSummary</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `lookupById`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#lookupbyidrequest'>LookupByIdRequest</a></code>|<code><a href='#project'>Project</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `lookupByIdBulk`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#lookupbyidbulkrequest'>LookupByIdBulkRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#project'>Project</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `lookupByPath`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#lookupbytitlerequest'>LookupByTitleRequest</a></code>|<code><a href='#project'>Project</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `lookupPrincipalInvestigator`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#lookupprincipalinvestigatorresponse'>LookupPrincipalInvestigatorResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `search`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#projectsearchbypathrequest'>ProjectSearchByPathRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#project'>Project</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `viewAncestors`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#project'>Project</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `viewMemberInProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: ADMIN, SERVICE, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=ADMIN,+SERVICE,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#viewmemberinprojectrequest'>ViewMemberInProjectRequest</a></code>|<code><a href='#viewmemberinprojectresponse'>ViewMemberInProjectResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `acceptInvite`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#acceptinviterequest'>AcceptInviteRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `archive`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#archiverequest'>ArchiveRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `archiveBulk`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#archivebulkrequest'>ArchiveBulkRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `changeUserRole`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#changeuserrolerequest'>ChangeUserRoleRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#createprojectrequest'>CreateProjectRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `deleteMember`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#deletememberrequest'>DeleteMemberRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `exists`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#existsrequest'>ExistsRequest</a></code>|<code><a href='#existsresponse'>ExistsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `invite`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#inviterequest'>InviteRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `leaveProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listIngoingInvites`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listingoinginvitesrequest'>ListIngoingInvitesRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#ingoinginvite'>IngoingInvite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listOutgoingInvites`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listoutgoinginvitesrequest'>ListOutgoingInvitesRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#outgoinginvite'>OutgoingInvite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listSubProjects`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listsubprojectsrequest'>ListSubProjectsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#memberinproject'>MemberInProject</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rejectInvite`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#rejectinviterequest'>RejectInviteRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rename`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#renameprojectrequest'>RenameProjectRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `toggleRenaming`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#togglerenamingrequest'>ToggleRenamingRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `transferPiRole`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#transferpirolerequest'>TransferPiRoleRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateDataManagementPlan`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#updatedatamanagementplanrequest'>UpdateDataManagementPlanRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `verifyMembership`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `viewProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#viewprojectrequest'>ViewProjectRequest</a></code>|<code><a href='#userprojectsummary'>UserProjectSummary</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `IngoingInvite`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IngoingInvite(
    val project: String,
    val title: String,
    val invitedBy: String,
    val timestamp: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>project</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>invitedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `MemberInProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class MemberInProject(
    val role: ProjectRole?,
    val project: Project,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>role</code>: <code><code><a href='#projectrole'>ProjectRole</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>project</code>: <code><code><a href='#project'>Project</a></code></code>
</summary>





</details>



</details>



---

### `OutgoingInvite`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class OutgoingInvite(
    val username: String,
    val invitedBy: String,
    val timestamp: Long,
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
<code>invitedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `Project`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Project(
    val id: String,
    val title: String,
    val parent: String?,
    val archived: Boolean,
    val fullPath: String?,
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
<code>parent</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>archived</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>fullPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ProjectMember`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProjectMember(
    val username: String,
    val role: ProjectRole,
    val memberOfAnyGroup: Boolean?,
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
<code>role</code>: <code><code><a href='#projectrole'>ProjectRole</a></code></code>
</summary>





</details>

<details>
<summary>
<code>memberOfAnyGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ProjectRole`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ProjectRole {
    PI,
    ADMIN,
    USER,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PI</code>
</summary>





</details>

<details>
<summary>
<code>ADMIN</code>
</summary>





</details>

<details>
<summary>
<code>USER</code>
</summary>





</details>



</details>



---

### `UserProjectSummary`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserProjectSummary(
    val projectId: String,
    val title: String,
    val whoami: ProjectMember,
    val needsVerification: Boolean,
    val isFavorite: Boolean,
    val archived: Boolean,
    val parent: String?,
    val ancestorPath: String?,
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

<details>
<summary>
<code>whoami</code>: <code><code><a href='#projectmember'>ProjectMember</a></code></code>
</summary>





</details>

<details>
<summary>
<code>needsVerification</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>isFavorite</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>archived</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>parent</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>ancestorPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `AcceptInviteRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AcceptInviteRequest(
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

### `AllowsRenamingRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AllowsRenamingRequest(
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

### `ArchiveBulkRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ArchiveBulkRequest(
    val projects: List<UserProjectSummary>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projects</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#userprojectsummary'>UserProjectSummary</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ArchiveRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ArchiveRequest(
    val archiveStatus: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>archiveStatus</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `ChangeUserRoleRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ChangeUserRoleRequest(
    val projectId: String,
    val member: String,
    val newRole: ProjectRole,
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
<code>member</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newRole</code>: <code><code><a href='#projectrole'>ProjectRole</a></code></code>
</summary>





</details>



</details>



---

### `CreateProjectRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CreateProjectRequest(
    val title: String,
    val parent: String?,
    val principalInvestigator: String?,
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
<code>parent</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>principalInvestigator</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `DeleteMemberRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DeleteMemberRequest(
    val projectId: String,
    val member: String,
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
<code>member</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ExistsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExistsRequest(
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

### `InviteRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class InviteRequest(
    val projectId: String,
    val usernames: List<String>,
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
<code>usernames</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ListFavoriteProjectsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListFavoriteProjectsRequest(
    val user: String?,
    val itemsPerPage: Int,
    val page: Int,
    val archived: Boolean,
    val showAncestorPath: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>user</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>archived</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>showAncestorPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ListIngoingInvitesRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListIngoingInvitesRequest(
    val itemsPerPage: Int?,
    val page: Int?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `ListOutgoingInvitesRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListOutgoingInvitesRequest(
    val itemsPerPage: Int?,
    val page: Int?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `ListProjectsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListProjectsRequest(
    val user: String?,
    val itemsPerPage: Int?,
    val page: Int?,
    val archived: Boolean?,
    val noFavorites: Boolean?,
    val showAncestorPath: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>user</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>archived</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>noFavorites</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>showAncestorPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ListSubProjectsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class ListSubProjectsRequest(
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

### `LookupByIdBulkRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupByIdBulkRequest(
    val ids: List<String>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ids</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `LookupByIdRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupByIdRequest(
    val id: String,
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



</details>



---

### `LookupByTitleRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupByTitleRequest(
    val title: String,
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



</details>



---

### `ProjectSearchByPathRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class ProjectSearchByPathRequest(
    val path: String,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val includeFullPath: Boolean?,
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
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
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

<details>
<summary>
<code>includeFullPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `RejectInviteRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RejectInviteRequest(
    val username: String?,
    val projectId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `RenameProjectRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RenameProjectRequest(
    val id: String,
    val newTitle: String,
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
<code>newTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ToggleRenamingRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ToggleRenamingRequest(
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

### `TransferPiRoleRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TransferPiRoleRequest(
    val newPrincipalInvestigator: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>newPrincipalInvestigator</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `UpdateDataManagementPlanRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdateDataManagementPlanRequest(
    val id: String,
    val dmp: String?,
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
<code>dmp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ViewMemberInProjectRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ViewMemberInProjectRequest(
    val projectId: String,
    val username: String,
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
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ViewProjectRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ViewProjectRequest(
    val id: String,
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



</details>



---

### `AllowsRenamingResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AllowsRenamingResponse(
    val allowed: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>allowed</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `ExistsResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExistsResponse(
    val exists: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>exists</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `FetchDataManagementPlanResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FetchDataManagementPlanResponse(
    val dmp: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>dmp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `LookupPrincipalInvestigatorResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupPrincipalInvestigatorResponse(
    val principalInvestigator: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>principalInvestigator</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ViewMemberInProjectResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ViewMemberInProjectResponse(
    val member: ProjectMember,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>member</code>: <code><code><a href='#projectmember'>ProjectMember</a></code></code>
</summary>





</details>



</details>



---


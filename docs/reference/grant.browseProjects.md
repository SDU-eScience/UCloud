[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `grant.browseProjects`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Endpoint for users to browse projects which they can send a [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)  to_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#browseprojectsrequest'>BrowseProjectsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#projectwithtitle'>ProjectWithTitle</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Concretely, this will return a list for which the user matches the criteria listed in
`ProjectApplicationSettings.allowRequestsFrom`.



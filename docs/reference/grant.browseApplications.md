[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `grant.browseApplications`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_List active [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)s_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#browseapplicationsrequest'>BrowseApplicationsRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#grantapplication'>GrantApplication</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Lists active `GrantApplication`s which are relevant to a project. By using
                    [`BrowseApplicationFlags`](/docs/reference/dk.sdu.cloud.grant.api.BrowseApplicationFlags.md)  it is possible to filter on ingoing and/or outgoing.



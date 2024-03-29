[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / [Allocation Process](/docs/developer-guide/accounting-and-projects/grants/grants.md)

# `grant.viewApplication`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves an active [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.md)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#retrieveapplicationrequest'>RetrieveApplicationRequest</a></code>|<code><a href='#grantapplication'>GrantApplication</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only the creator and grant reviewers are allowed to view any given [`GrantApplication`](/docs/reference/dk.sdu.cloud.grant.api.GrantApplication..md)



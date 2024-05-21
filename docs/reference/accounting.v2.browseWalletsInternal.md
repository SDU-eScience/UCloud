[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `accounting.v2.browseWalletsInternal`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a list of up-to-date wallets_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browsewalletsinternal.request'>AccountingV2.BrowseWalletsInternal.Request</a></code>|<code><a href='#accountingv2.browsewalletsinternal.response'>AccountingV2.BrowseWalletsInternal.Response</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will return a list of [`Wallet`](/docs/reference/dk.sdu.cloud.accounting.api.Wallet.md)s which are related to the active 
workspace. This is mainly for backend use. For frontend, use the browse call instead for a
paginated response



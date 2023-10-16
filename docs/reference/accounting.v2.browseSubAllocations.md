[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `accounting.v2.browseSubAllocations`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of sub-allocations_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browsesuballocations.request'>AccountingV2.BrowseSubAllocations.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#suballocationv2'>SubAllocationV2</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will find all [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s which are direct children of one of your
accessible [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s.



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# `accounting.v2.retrieveAllocationsInternal`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a list of product specific up-to-date allocation from the in-memory DB_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browseallocationsinternal.request'>AccountingV2.BrowseAllocationsInternal.Request</a></code>|<code><a href='#accountingv2.browseallocationsinternal.response'>AccountingV2.BrowseAllocationsInternal.Response</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will return a list of [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s which are related to the given product
available to the user.
This is mainly for backend use. For frontend, use the browse call instead for a paginated response



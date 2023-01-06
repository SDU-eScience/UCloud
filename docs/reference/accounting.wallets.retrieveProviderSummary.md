[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Wallets](/docs/developer-guide/accounting-and-projects/accounting/wallets.md)

# `accounting.wallets.retrieveProviderSummary`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a provider summary of relevant wallets_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#walletsretrieveprovidersummaryrequest'>WalletsRetrieveProviderSummaryRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#providerwalletsummary'>ProviderWalletSummary</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is only usable by providers. The endpoint will return a stable sorted summary of all
allocations that a provider currently has.



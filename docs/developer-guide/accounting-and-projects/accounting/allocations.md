<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/products.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/accounting/visualization.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / Accounting
# Accounting

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_The goal of UCloud's accounting system is to:_

## Rationale

1. Allow or deny access to a provider's service catalog
2. Track consumption of resources at the workspace level
3. Generate visualizations and reports which track historical consumption data

## Allocations: Granting access to a service catalog

UCloud achieves the first point by having the ability to grant resource allocations. A resource allocation 
is also known as a `WalletAllocation`. They grant a workspace the ability to use `Product`s from a 
specific `ProductCategory`. Unless otherwise stated, a workspace must always hold an allocation to use a 
product. If a workspace does not hold an allocation, then the accounting system will deny access to them. 
An allocation sets several limits on how the workspace can use the products. This includes:

- An allocation is only valid for the `Product`s belonging to a single category. For example, if a 
  workspace has an allocation for `u1-standard` then it does not grant access to `u1-gpu`.
- An allocation has a start date and an end date. Outside of this period, the allocation is invalid.
- Each allocation have an associated quota. If a workspace is using more than the quota allows, then the 
  provider should deny access to the `Product`.

---

__📝NOTE:__ It is the responsibility of the provider and not UCloud's accounting system to deny access 
to a resource when the quota is exceeded. UCloud assists in this process by telling providers when a 
workspace exceeds their quota. But the responsibility lies with the providers, as they usually have more 
information. UCloud will only check for the existence of a valid allocation before forwarding the request.

---

Resource allocations are hierarchical in UCloud. In practice, this means that all allocations can have 
either 0 or 1 parent allocation. Allocations which do not have a parent are root allocations. Only UCloud 
administrators/provider administrators can create root allocations. Administrators of a workspace can 
"sub-allocate" their own allocations. This will create a new allocation which has one of their existing 
allocations as the parent. UCloud allows for over-allocation when creating sub-allocations. UCloud avoids 
over-spending by making sure that the usage in a sub-tree doesn't exceed the quota specified in the root 
of the sub-tree. For example, consider the following sub-allocation created by a workspace administrator:

![](/backend/accounting-service/wiki/allocations-2-1.png)

They can even create another which is even larger.

![](/backend/accounting-service/wiki/allocations-2-2.png)

The sub-allocations themselves can continue to create new sub-allocations. These hierarchies can be as 
complex as they need to be.

![](/backend/accounting-service/wiki/allocations-2-3.png)

In the above example neither "Research 1" or "Research 2" can have a usage above 10GB due to their 
parent. Similarly, if the combined usage goes above 10GB then UCloud will lock both of the allocations.

### Summary

__Important concepts:__

- [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md): Stores a resource allocation which grants a workspace access to a
  ProductCategory
- [`Wallet`](/docs/reference/dk.sdu.cloud.accounting.api.Wallet.md): Combines multiple allocations, belonging to the same workspace for a specific category.
  The accounting system spreads out usages evenly across all allocations in a Wallet.
- Allocations form a hierarchy. Over-allocation is allowed but the combined usage in a single allocation 
  tree must not exceed the quota in the root.
  
__Important calls:__

- [`accounting.v2.rootAllocate`](/docs/reference/accounting.v2.rootAllocate.md)  and [`accounting.v2.subAllocate`](/docs/reference/accounting.v2.subAllocate.md): Create new allocations.
- [`accounting.v2.updateAllocation`](/docs/reference/accounting.v2.updateAllocation.md): Update an allocation.
- [`accounting.v2.browseSubAllocations`](/docs/reference/accounting.v2.browseSubAllocations.md), [`accounting.v2.searchSubAllocations`](/docs/reference/accounting.v2.searchSubAllocations.md)  [`accounting.v2.browseAllocationsInternal`](/docs/reference/accounting.v2.browseAllocationsInternal.md): Browse 
  through your sub allocations.
- [`accounting.v2.browseWallets`](/docs/reference/accounting.v2.browseWallets.md)  and [`accounting.v2.browseWalletsInternal`](/docs/reference/accounting.v2.browseWalletsInternal.md): Browse through your wallets.

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
<td><a href='#browsesuballocations'><code>browseSubAllocations</code></a></td>
<td>Browses the catalog of sub-allocations</td>
</tr>
<tr>
<td><a href='#browsewallets'><code>browseWallets</code></a></td>
<td>Browses the catalog of accessible Wallets</td>
</tr>
<tr>
<td><a href='#retrieveallocationrecipient'><code>retrieveAllocationRecipient</code></a></td>
<td>Retrieves information about a potential WalletAllocation recipient</td>
</tr>
<tr>
<td><a href='#retrieveallocationsinternal'><code>retrieveAllocationsInternal</code></a></td>
<td>Retrieves a list of product specific up-to-date allocation from the in-memory DB</td>
</tr>
<tr>
<td><a href='#searchsuballocations'><code>searchSubAllocations</code></a></td>
<td>Searches the catalog of sub-allocations</td>
</tr>
<tr>
<td><a href='#browseproviderallocations'><code>browseProviderAllocations</code></a></td>
<td>Browses allocations relevant for a specific provider</td>
</tr>
<tr>
<td><a href='#browsewalletsinternal'><code>browseWalletsInternal</code></a></td>
<td>Retrieves a list of up-to-date wallets</td>
</tr>
<tr>
<td><a href='#check'><code>check</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findrelevantproviders'><code>findRelevantProviders</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#reportdelta'><code>reportDelta</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#reporttotalusage'><code>reportTotalUsage</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rootallocate'><code>rootAllocate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#suballocate'><code>subAllocate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateallocation'><code>updateAllocation</code></a></td>
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
<td><a href='#wallet'><code>Wallet</code></a></td>
<td>Wallets hold allocations which grant access to a provider's resources.</td>
</tr>
<tr>
<td><a href='#walletallocation'><code>WalletAllocation</code></a></td>
<td>An allocation grants access to resources</td>
</tr>
<tr>
<td><a href='#accountingfrequency'><code>AccountingFrequency</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingunit'><code>AccountingUnit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingunitconversion'><code>AccountingUnitConversion</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#allocationselectorpolicy'><code>AllocationSelectorPolicy</code></a></td>
<td>A policy for how to select a WalletAllocation in a single Wallet</td>
</tr>
<tr>
<td><a href='#chargedescription'><code>ChargeDescription</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#itemizedcharge'><code>ItemizedCharge</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#productcategory'><code>ProductCategory</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#providerwalletsummaryv2'><code>ProviderWalletSummaryV2</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#suballocationv2'><code>SubAllocationV2</code></a></td>
<td>A parent allocator's view of a `WalletAllocation`</td>
</tr>
<tr>
<td><a href='#usagereportitem'><code>UsageReportItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#walletallocationv2'><code>WalletAllocationV2</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#walletv2'><code>WalletV2</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.browseallocationsinternal.request'><code>AccountingV2.BrowseAllocationsInternal.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.browsesuballocations.request'><code>AccountingV2.BrowseSubAllocations.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.browsewallets.request'><code>AccountingV2.BrowseWallets.Request</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#accountingv2.browsewalletsinternal.request'><code>AccountingV2.BrowseWalletsInternal.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.findrelevantproviders.requestitem'><code>AccountingV2.FindRelevantProviders.RequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.retrieveallocationrecipient.request'><code>AccountingV2.RetrieveAllocationRecipient.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.rootallocate.requestitem'><code>AccountingV2.RootAllocate.RequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.searchsuballocations.request'><code>AccountingV2.SearchSubAllocations.Request</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.suballocate.requestitem'><code>AccountingV2.SubAllocate.RequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.updateallocation.requestitem'><code>AccountingV2.UpdateAllocation.RequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#walletsretrieveprovidersummaryrequest'><code>WalletsRetrieveProviderSummaryRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#accountingv2.browseallocationsinternal.response'><code>AccountingV2.BrowseAllocationsInternal.Response</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.browsewalletsinternal.response'><code>AccountingV2.BrowseWalletsInternal.Response</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.findrelevantproviders.response'><code>AccountingV2.FindRelevantProviders.Response</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingv2.retrieveallocationrecipient.response'><code>AccountingV2.RetrieveAllocationRecipient.Response</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browseSubAllocations`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of sub-allocations_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browsesuballocations.request'>AccountingV2.BrowseSubAllocations.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#suballocationv2'>SubAllocationV2</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will find all [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s which are direct children of one of your
accessible [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s.


### `browseWallets`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of accessible Wallets_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browsewallets.request'>AccountingV2.BrowseWallets.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#wallet'>Wallet</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveAllocationRecipient`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves information about a potential WalletAllocation recipient_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.retrieveallocationrecipient.request'>AccountingV2.RetrieveAllocationRecipient.Request</a></code>|<code><a href='#accountingv2.retrieveallocationrecipient.response'>AccountingV2.RetrieveAllocationRecipient.Response</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

You can use this endpoint to find information about a Workspace. This is useful when creating a 
sub-allocation.


### `retrieveAllocationsInternal`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a list of product specific up-to-date allocation from the in-memory DB_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browseallocationsinternal.request'>AccountingV2.BrowseAllocationsInternal.Request</a></code>|<code><a href='#accountingv2.browseallocationsinternal.response'>AccountingV2.BrowseAllocationsInternal.Response</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will return a list of [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s which are related to the given product
available to the user.
This is mainly for backend use. For frontend, use the browse call instead for a paginated response


### `searchSubAllocations`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalog of sub-allocations_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.searchsuballocations.request'>AccountingV2.SearchSubAllocations.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#suballocationv2'>SubAllocationV2</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will find all [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s which are direct children of one of your
accessible [`WalletAllocation`](/docs/reference/dk.sdu.cloud.accounting.api.WalletAllocation.md)s.


### `browseProviderAllocations`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses allocations relevant for a specific provider_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#walletsretrieveprovidersummaryrequest'>WalletsRetrieveProviderSummaryRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#providerwalletsummaryv2'>ProviderWalletSummaryV2</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is only usable by providers. The endpoint will return a stable results.


### `browseWalletsInternal`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a list of up-to-date wallets_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#accountingv2.browsewalletsinternal.request'>AccountingV2.BrowseWalletsInternal.Request</a></code>|<code><a href='#accountingv2.browsewalletsinternal.response'>AccountingV2.BrowseWalletsInternal.Response</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will return a list of [`Wallet`](/docs/reference/dk.sdu.cloud.accounting.api.Wallet.md)s which are related to the active 
workspace. This is mainly for backend use. For frontend, use the browse call instead for a
paginated response


### `check`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#usagereportitem'>UsageReportItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findRelevantProviders`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#accountingv2.findrelevantproviders.requestitem'>AccountingV2.FindRelevantProviders.RequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#accountingv2.findrelevantproviders.response'>AccountingV2.FindRelevantProviders.Response</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `reportDelta`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#usagereportitem'>UsageReportItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `reportTotalUsage`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#usagereportitem'>UsageReportItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rootAllocate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Admin](https://img.shields.io/static/v1?label=Auth&message=Admin&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#accountingv2.rootallocate.requestitem'>AccountingV2.RootAllocate.RequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `subAllocate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#accountingv2.suballocate.requestitem'>AccountingV2.SubAllocate.RequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAllocation`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#accountingv2.updateallocation.requestitem'>AccountingV2.UpdateAllocation.RequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Wallet`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Wallets hold allocations which grant access to a provider's resources._

```kotlin
data class Wallet(
    val owner: WalletOwner,
    val paysFor: ProductCategoryId,
    val allocations: List<WalletAllocation>,
    val chargePolicy: AllocationSelectorPolicy,
    val productType: ProductType?,
    val chargeType: ChargeType?,
    val unit: ProductPriceUnit?,
)
```
You can find more information about WalletAllocations
[here](/docs/developer-guide/accounting-and-projects/accounting/wallets.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>paysFor</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryId.md'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>allocations</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#walletallocation'>WalletAllocation</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>chargePolicy</code>: <code><code><a href='#allocationselectorpolicy'>AllocationSelectorPolicy</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unit</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md'>ProductPriceUnit</a>?</code></code>
</summary>





</details>



</details>



---

### `WalletAllocation`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_An allocation grants access to resources_

```kotlin
data class WalletAllocation(
    val id: String,
    val allocationPath: List<String>,
    val balance: Long,
    val initialBalance: Long,
    val localBalance: Long,
    val startDate: Long,
    val endDate: Long?,
    val grantedIn: Long?,
    val maxUsableBalance: Long?,
    val canAllocate: Boolean?,
    val allowSubAllocationsToAllocate: Boolean?,
)
```
You can find more information about WalletAllocations
[here](/docs/developer-guide/accounting-and-projects/accounting/wallets.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique ID of this allocation
</summary>





</details>

<details>
<summary>
<code>allocationPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A path, starting from the top, through the allocations that will be charged, when a charge is made
</summary>



Note that this allocation path will always include, as its last element, this allocation.


</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The current balance of this wallet allocation's subtree
</summary>





</details>

<details>
<summary>
<code>initialBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The initial balance which was granted to this allocation
</summary>





</details>

<details>
<summary>
<code>localBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The current balance of this wallet allocation
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp for when this allocation becomes valid
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Timestamp for when this allocation becomes invalid, null indicates that this allocation does not expire automatically
</summary>





</details>

<details>
<summary>
<code>grantedIn</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> ID reference to which grant application this allocation was granted in
</summary>





</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>canAllocate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A property which indicates if this allocation can be used to create sub-allocations
</summary>





</details>

<details>
<summary>
<code>allowSubAllocationsToAllocate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A property which indicates that new sub-allocations of this allocation by default should have canAllocate = true
</summary>





</details>



</details>



---

### `AccountingFrequency`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class AccountingFrequency {
    ONCE,
    PERIODIC_MINUTE,
    PERIODIC_HOUR,
    PERIODIC_DAY,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ONCE</code>
</summary>





</details>

<details>
<summary>
<code>PERIODIC_MINUTE</code>
</summary>





</details>

<details>
<summary>
<code>PERIODIC_HOUR</code>
</summary>





</details>

<details>
<summary>
<code>PERIODIC_DAY</code>
</summary>





</details>



</details>



---

### `AccountingUnit`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AccountingUnit(
    val name: String,
    val namePlural: String,
    val floatingPoint: Boolean,
    val displayFrequencySuffix: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>namePlural</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>floatingPoint</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>displayFrequencySuffix</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `AccountingUnitConversion`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AccountingUnitConversion(
    val factor: Double,
    val destinationUnit: AccountingUnit,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>factor</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code>
</summary>





</details>

<details>
<summary>
<code>destinationUnit</code>: <code><code><a href='#accountingunit'>AccountingUnit</a></code></code>
</summary>





</details>



</details>



---

### `AllocationSelectorPolicy`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A policy for how to select a WalletAllocation in a single Wallet_

```kotlin
enum class AllocationSelectorPolicy {
    EXPIRE_FIRST,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>EXPIRE_FIRST</code> Use the WalletAllocation which is closest to expiration
</summary>





</details>



</details>



---

### `ChargeDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ChargeDescription(
    val description: String,
    val itemized: List<ItemizedCharge>,
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

<details>
<summary>
<code>itemized</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#itemizedcharge'>ItemizedCharge</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ItemizedCharge`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ItemizedCharge(
    val description: String,
    val usage: Long?,
    val productId: String?,
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

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ProductCategory`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProductCategory(
    val name: String,
    val provider: String,
    val productType: ProductType,
    val accountingUnit: AccountingUnit,
    val accountingFrequency: AccountingFrequency,
    val conversionTable: List<AccountingUnitConversion>?,
    val freeToUse: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>provider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>accountingUnit</code>: <code><code><a href='#accountingunit'>AccountingUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>accountingFrequency</code>: <code><code><a href='#accountingfrequency'>AccountingFrequency</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conversionTable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#accountingunitconversion'>AccountingUnitConversion</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product category
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>



</details>



---

### `ProviderWalletSummaryV2`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProviderWalletSummaryV2(
    val id: String,
    val owner: WalletOwner,
    val categoryId: ProductCategory,
    val notBefore: Long,
    val notAfter: Long?,
    val quota: Long,
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
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The earliest timestamp which allows for the balance to be consumed
</summary>





</details>

<details>
<summary>
<code>notAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The earliest timestamp at which the reported balance is no longer fully usable
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `SubAllocationV2`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A parent allocator's view of a `WalletAllocation`_

```kotlin
data class SubAllocationV2(
    val id: String,
    val path: String,
    val startDate: Long,
    val endDate: Long?,
    val productCategory: ProductCategory,
    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceIsProject: Boolean,
    val projectPI: String?,
    val usage: Long,
    val quota: Long,
    val grantedIn: Long?,
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
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productCategory</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code>
</summary>





</details>

<details>
<summary>
<code>workspaceId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>workspaceTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>workspaceIsProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectPI</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantedIn</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>



</details>



---

### `UsageReportItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UsageReportItem(
    val owner: WalletOwner,
    val categoryIdV2: ProductCategoryIdV2,
    val usage: Long,
    val description: ChargeDescription,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>categoryIdV2</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryIdV2.md'>ProductCategoryIdV2</a></code></code>
</summary>





</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='#chargedescription'>ChargeDescription</a></code></code>
</summary>





</details>



</details>



---

### `WalletAllocationV2`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class WalletAllocationV2(
    val id: String,
    val allocationPath: List<String>,
    val localUsage: Long,
    val quota: Long,
    val treeUsage: Long?,
    val startDate: Long,
    val endDate: Long,
    val grantedIn: Long?,
    val deicAllocationId: String?,
    val canAllocate: Boolean?,
    val allowSubAllocationsToAllocate: Boolean?,
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
<code>allocationPath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>localUsage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>treeUsage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>grantedIn</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>deicAllocationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>canAllocate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowSubAllocationsToAllocate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `WalletV2`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class WalletV2(
    val owner: WalletOwner,
    val paysFor: ProductCategory,
    val allocations: List<WalletAllocationV2>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>paysFor</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code>
</summary>





</details>

<details>
<summary>
<code>allocations</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#walletallocationv2'>WalletAllocationV2</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.BrowseAllocationsInternal.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val owner: WalletOwner,
    val categoryId: ProductCategoryIdV2,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryIdV2.md'>ProductCategoryIdV2</a></code></code>
</summary>





</details>



</details>



---

### `AccountingV2.BrowseSubAllocations.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val filterType: ProductType?,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
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

### `AccountingV2.BrowseWallets.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class Request(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val filterEmptyAllocations: Boolean?,
    val includeMaxUsableBalance: Boolean?,
    val filterType: ProductType?,
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
<code>filterEmptyAllocations</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeMaxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.BrowseWalletsInternal.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val owner: WalletOwner,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>



</details>



---

### `AccountingV2.FindRelevantProviders.RequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RequestItem(
    val username: String,
    val project: String?,
    val useProject: Boolean,
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
<code>project</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>useProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `AccountingV2.RetrieveAllocationRecipient.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val query: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `AccountingV2.RootAllocate.RequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RequestItem(
    val owner: WalletOwner,
    val productCategory: ProductCategoryIdV2,
    val quota: Long,
    val start: Long,
    val end: Long,
    val deicAllocationId: String?,
    val forcedSync: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productCategory</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryIdV2.md'>ProductCategoryIdV2</a></code></code>
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>start</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>end</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>deicAllocationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>forcedSync</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.SearchSubAllocations.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Request(
    val query: String,
    val filterType: ProductType?,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>filterType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
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

### `AccountingV2.SubAllocate.RequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RequestItem(
    val parentAllocation: String,
    val owner: WalletOwner,
    val quota: Long,
    val start: Long,
    val end: Long?,
    val dry: Boolean?,
    val grantedIn: Long?,
    val deicAllocationId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>parentAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>quota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>start</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>end</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>dry</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>grantedIn</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>deicAllocationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.UpdateAllocation.RequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RequestItem(
    val allocationId: String,
    val newQuota: Long?,
    val newStart: Long?,
    val newEnd: Long?,
    val reason: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>allocationId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newQuota</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newStart</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newEnd</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>reason</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `WalletsRetrieveProviderSummaryRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class WalletsRetrieveProviderSummaryRequest(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val filterOwnerId: String?,
    val filterOwnerIsProject: Boolean?,
    val filterCategory: String?,
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
<code>filterOwnerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterOwnerIsProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.BrowseAllocationsInternal.Response`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val allocations: List<WalletAllocationV2>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>allocations</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#walletallocationv2'>WalletAllocationV2</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.BrowseWalletsInternal.Response`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val wallets: List<WalletV2>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>wallets</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#walletv2'>WalletV2</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.FindRelevantProviders.Response`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val providers: List<String>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `AccountingV2.RetrieveAllocationRecipient.Response`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Response(
    val id: String,
    val isProject: Boolean,
    val title: String,
    val principalInvestigator: String,
    val numberOfMembers: Int,
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
<code>isProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>principalInvestigator</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>numberOfMembers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---


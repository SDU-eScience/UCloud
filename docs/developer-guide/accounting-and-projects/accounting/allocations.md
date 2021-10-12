<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/accounting/wallets.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/accounting/visualization.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / Accounting Operations
# Accounting Operations

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_The accounting system of UCloud has three core operations._

## Rationale

The three core operations of the UCloud accounting system are:

- [`accounting.charge`](/docs/reference/accounting.charge.md): Records usage in the system. For absolute payment models, this will deduct 
  the balance and local balance of an allocation. All ancestor allocations have their balance deducted by 
  the same amount. The local balances of an ancestor remains unchanged. 
- [`accounting.deposit`](/docs/reference/accounting.deposit.md): Creates a new _sub-allocation_ from a parent allocation. The new allocation
  will have the current allocation as a parent. The balance of the parent allocation is not changed.
- [`accounting.transfer`](/docs/reference/accounting.transfer.md): Creates a new root allocation from a parent allocation. The new allocation 
  will have no parents. The balance of the parent allocation is immediately removed, in full.

---

__📝 NOTE:__ We recommend that you first read and understand the 
[Wallet system](/docs/developer-guide/accounting-and-projects/accounting/wallets.md) of UCloud.

---

__📝 Provider Note:__ This API is invoked by internal UCloud/Core services. As a 
[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md), you will be indirectly calling this API through the outgoing
`Control` APIs.

---

We recommend that you study the examples below and look at the corresponding call documentation to 
understand the accounting system of UCloud.

## A note on the examples

In the examples below, we will be using a consistent set of [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s:

- `example-slim-1` / `example-slim` @ `example`
   - Type: Compute
   - `ChargeType.ABSOLUTE`
   - `ProductPriceUnit.UNITS_PER_HOUR`
   - Price per unit: 1
- `example-storage` / `example-storage` @ `example`
   - Type: Storage
   - `ChargeType.DIFFERENTIAL_QUOTA`
   - `ProductPriceUnit.PER_UNIT`
   - Price per unit: 1

## Table of Contents
<details>
<summary>
<a href='#example-charging-a-root-allocation-(absolute)'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-charging-a-root-allocation-(absolute)'>Charging a root allocation (Absolute)</a></td></tr>
<tr><td><a href='#example-charging-a-root-allocation-(differential)'>Charging a root allocation (Differential)</a></td></tr>
<tr><td><a href='#example-charging-a-leaf-allocation-(absolute)'>Charging a leaf allocation (Absolute)</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#charge'><code>charge</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#check'><code>check</code></a></td>
<td>Checks if one or more wallets are able to carry a charge</td>
</tr>
<tr>
<td><a href='#deposit'><code>deposit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rootdeposit'><code>rootDeposit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transfer'><code>transfer</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateallocation'><code>updateAllocation</code></a></td>
<td>Update an existing allocation</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#chargewalletrequestitem'><code>ChargeWalletRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deposittowalletrequestitem'><code>DepositToWalletRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rootdepositrequestitem'><code>RootDepositRequestItem</code></a></td>
<td>See `DepositToWalletRequestItem`</td>
</tr>
<tr>
<td><a href='#transfertowalletrequestitem'><code>TransferToWalletRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateallocationrequestitem'><code>UpdateAllocationRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Charging a root allocation (Absolute)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will be performing some simple charge requests for an absolute 
product. Before and after each charge, we will show the current state of the system.
We will perform the charges on a root allocation, that is, it has no ancestors. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    ucloud
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 1000, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* Currently, the allocation has a balance of 1000. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        numberOfProducts = 1, 
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        product = ProductReference(
            category = "example-slim", 
            id = "example-slim-1", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 1, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(true), 
)
*/

/* The charge returns true, indicating that we had enough credits to complete the request. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    ucloud
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 999, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 999, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* As expected, a single credit was removed from our current balance and local balance. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        numberOfProducts = 1, 
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        product = ProductReference(
            category = "example-slim", 
            id = "example-slim-1", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 1, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(true), 
)
*/
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    ucloud
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 998, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 998, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* A second charge further deducts 1 from the balance, as expected. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will be performing some simple charge requests for an absolute 
product. Before and after each charge, we will show the current state of the system.
We will perform the charges on a root allocation, that is, it has no ancestors. */

// Authenticated as ucloud
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "my-research"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 1000,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/

/* Currently, the allocation has a balance of 1000. */

await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "my-research"
                },
                "units": 1,
                "numberOfProducts": 1,
                "product": {
                    "id": "example-slim-1",
                    "category": "example-slim",
                    "provider": "example"
                },
                "performedBy": "user",
                "description": "A charge for compute usage",
                "transactionId": "charge-1"
            }
        ]
    }
);

/*
{
    "responses": [
        true
    ]
}
*/

/* The charge returns true, indicating that we had enough credits to complete the request. */

await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "my-research"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 999,
                    "initialBalance": 1000,
                    "localBalance": 999,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/

/* As expected, a single credit was removed from our current balance and local balance. */

await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "my-research"
                },
                "units": 1,
                "numberOfProducts": 1,
                "product": {
                    "id": "example-slim-1",
                    "category": "example-slim",
                    "provider": "example"
                },
                "performedBy": "user",
                "description": "A charge for compute usage",
                "transactionId": "charge-1"
            }
        ]
    }
);

/*
{
    "responses": [
        true
    ]
}
*/
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "my-research"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 998,
                    "initialBalance": 1000,
                    "localBalance": 998,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/

/* A second charge further deducts 1 from the balance, as expected. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# In this example, we will be performing some simple charge requests for an absolute 
# product. Before and after each charge, we will show the current state of the system.
# We will perform the charges on a root allocation, that is, it has no ancestors.

# Authenticated as ucloud
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "my-research"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 1000,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# Currently, the allocation has a balance of 1000.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "my-research"
            },
            "units": 1,
            "numberOfProducts": 1,
            "product": {
                "id": "example-slim-1",
                "category": "example-slim",
                "provider": "example"
            },
            "performedBy": "user",
            "description": "A charge for compute usage",
            "transactionId": "charge-1"
        }
    ]
}'


# {
#     "responses": [
#         true
#     ]
# }

# The charge returns true, indicating that we had enough credits to complete the request.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "my-research"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 999,
#                     "initialBalance": 1000,
#                     "localBalance": 999,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# As expected, a single credit was removed from our current balance and local balance.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "my-research"
            },
            "units": 1,
            "numberOfProducts": 1,
            "product": {
                "id": "example-slim-1",
                "category": "example-slim",
                "provider": "example"
            },
            "performedBy": "user",
            "description": "A charge for compute usage",
            "transactionId": "charge-1"
        }
    ]
}'


# {
#     "responses": [
#         true
#     ]
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "my-research"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 998,
#                     "initialBalance": 1000,
#                     "localBalance": 998,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# A second charge further deducts 1 from the balance, as expected.

```


</details>


## Example: Charging a root allocation (Differential)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will be performing some simple charge requests for a differential 
product. Before and after each charge, we will show the current state of the system.
We will perform the charges on a root allocation, that is, it has no ancestors. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    ucloud
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 1000, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-storage", 
            name = "example-storage", 
            provider = "example", 
        ), 
        productType = ProductType.STORAGE, 
        unit = ProductPriceUnit.PER_UNIT, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* Currently, the allocation shows that we have 1000 GB unused. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for storage usage", 
        numberOfProducts = 1, 
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        product = ProductReference(
            category = "example-storage", 
            id = "example-storage", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 100, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(true), 
)
*/

/* The charge returns true, indicating that we had enough credits to complete the request. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    ucloud
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 900, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 900, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-storage", 
            name = "example-storage", 
            provider = "example", 
        ), 
        productType = ProductType.STORAGE, 
        unit = ProductPriceUnit.PER_UNIT, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* The charge has correctly record our usage. It now shows that we have 900 GB unused. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for storage usage", 
        numberOfProducts = 1, 
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        product = ProductReference(
            category = "example-storage", 
            id = "example-storage", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 50, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(true), 
)
*/

/* The new charge reports that we are only using 50 GB, that is data was removed since last period. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    ucloud
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 950, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 950, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-storage", 
            name = "example-storage", 
            provider = "example", 
        ), 
        productType = ProductType.STORAGE, 
        unit = ProductPriceUnit.PER_UNIT, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* This results in 950 GB being unused. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will be performing some simple charge requests for a differential 
product. Before and after each charge, we will show the current state of the system.
We will perform the charges on a root allocation, that is, it has no ancestors. */

// Authenticated as ucloud
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "my-research"
            },
            "paysFor": {
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 1000,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "STORAGE",
            "chargeType": "DIFFERENTIAL_QUOTA",
            "unit": "PER_UNIT"
        }
    ],
    "next": null
}
*/

/* Currently, the allocation shows that we have 1000 GB unused. */

await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "my-research"
                },
                "units": 100,
                "numberOfProducts": 1,
                "product": {
                    "id": "example-storage",
                    "category": "example-storage",
                    "provider": "example"
                },
                "performedBy": "user",
                "description": "A charge for storage usage",
                "transactionId": "charge-1"
            }
        ]
    }
);

/*
{
    "responses": [
        true
    ]
}
*/

/* The charge returns true, indicating that we had enough credits to complete the request. */

await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "my-research"
            },
            "paysFor": {
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 900,
                    "initialBalance": 1000,
                    "localBalance": 900,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "STORAGE",
            "chargeType": "DIFFERENTIAL_QUOTA",
            "unit": "PER_UNIT"
        }
    ],
    "next": null
}
*/

/* The charge has correctly record our usage. It now shows that we have 900 GB unused. */

await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "my-research"
                },
                "units": 50,
                "numberOfProducts": 1,
                "product": {
                    "id": "example-storage",
                    "category": "example-storage",
                    "provider": "example"
                },
                "performedBy": "user",
                "description": "A charge for storage usage",
                "transactionId": "charge-1"
            }
        ]
    }
);

/*
{
    "responses": [
        true
    ]
}
*/

/* The new charge reports that we are only using 50 GB, that is data was removed since last period. */

await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "my-research"
            },
            "paysFor": {
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 950,
                    "initialBalance": 1000,
                    "localBalance": 950,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "STORAGE",
            "chargeType": "DIFFERENTIAL_QUOTA",
            "unit": "PER_UNIT"
        }
    ],
    "next": null
}
*/

/* This results in 950 GB being unused. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# In this example, we will be performing some simple charge requests for a differential 
# product. Before and after each charge, we will show the current state of the system.
# We will perform the charges on a root allocation, that is, it has no ancestors.

# Authenticated as ucloud
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "my-research"
#             },
#             "paysFor": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 1000,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "STORAGE",
#             "chargeType": "DIFFERENTIAL_QUOTA",
#             "unit": "PER_UNIT"
#         }
#     ],
#     "next": null
# }

# Currently, the allocation shows that we have 1000 GB unused.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "my-research"
            },
            "units": 100,
            "numberOfProducts": 1,
            "product": {
                "id": "example-storage",
                "category": "example-storage",
                "provider": "example"
            },
            "performedBy": "user",
            "description": "A charge for storage usage",
            "transactionId": "charge-1"
        }
    ]
}'


# {
#     "responses": [
#         true
#     ]
# }

# The charge returns true, indicating that we had enough credits to complete the request.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "my-research"
#             },
#             "paysFor": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 900,
#                     "initialBalance": 1000,
#                     "localBalance": 900,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "STORAGE",
#             "chargeType": "DIFFERENTIAL_QUOTA",
#             "unit": "PER_UNIT"
#         }
#     ],
#     "next": null
# }

# The charge has correctly record our usage. It now shows that we have 900 GB unused.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "my-research"
            },
            "units": 50,
            "numberOfProducts": 1,
            "product": {
                "id": "example-storage",
                "category": "example-storage",
                "provider": "example"
            },
            "performedBy": "user",
            "description": "A charge for storage usage",
            "transactionId": "charge-1"
        }
    ]
}'


# {
#     "responses": [
#         true
#     ]
# }

# The new charge reports that we are only using 50 GB, that is data was removed since last period.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "my-research"
#             },
#             "paysFor": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 950,
#                     "initialBalance": 1000,
#                     "localBalance": 950,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "STORAGE",
#             "chargeType": "DIFFERENTIAL_QUOTA",
#             "unit": "PER_UNIT"
#         }
#     ],
#     "next": null
# }

# This results in 950 GB being unused.

```


</details>


## Example: Charging a leaf allocation (Absolute)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>piRoot (<code>The PI of the root project</code>)</li>
<li>piLeaf (<code>The PI of the leaf project</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    The PI of the root project
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 1000, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    The PI of the leaf project
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 500, 
            endDate = null, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 500, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        numberOfProducts = 1, 
        payer = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        performedBy = "user", 
        product = ProductReference(
            category = "example-slim", 
            id = "example-slim-1", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 1, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(true), 
)
*/
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    The PI of the root project
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 999, 
            endDate = null, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    The PI of the leaf project
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 499, 
            endDate = null, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 499, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.UNITS_PER_HOUR, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as The PI of the root project
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "root-project"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 1000,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/
// Authenticated as The PI of the leaf project
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": 500,
                    "initialBalance": 500,
                    "localBalance": 500,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/
// Authenticated as ucloud
await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "leaf-project"
                },
                "units": 1,
                "numberOfProducts": 1,
                "product": {
                    "id": "example-slim-1",
                    "category": "example-slim",
                    "provider": "example"
                },
                "performedBy": "user",
                "description": "A charge for compute usage",
                "transactionId": "charge-1"
            }
        ]
    }
);

/*
{
    "responses": [
        true
    ]
}
*/
// Authenticated as The PI of the root project
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "root-project"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 999,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/
// Authenticated as The PI of the leaf project
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": 499,
                    "initialBalance": 500,
                    "localBalance": 499,
                    "startDate": 1633941615074,
                    "endDate": null
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "UNITS_PER_HOUR"
        }
    ],
    "next": null
}
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as The PI of the root project
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "root-project"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 1000,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# Authenticated as The PI of the leaf project
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "leaf-project"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": 500,
#                     "initialBalance": 500,
#                     "localBalance": 500,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "units": 1,
            "numberOfProducts": 1,
            "product": {
                "id": "example-slim-1",
                "category": "example-slim",
                "provider": "example"
            },
            "performedBy": "user",
            "description": "A charge for compute usage",
            "transactionId": "charge-1"
        }
    ]
}'


# {
#     "responses": [
#         true
#     ]
# }

# Authenticated as The PI of the root project
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "root-project"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 999,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# Authenticated as The PI of the leaf project
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "leaf-project"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": 499,
#                     "initialBalance": 500,
#                     "localBalance": 499,
#                     "startDate": 1633941615074,
#                     "endDate": null
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

```


</details>



## Remote Procedure Calls

### `charge`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#chargewalletrequestitem'>ChargeWalletRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `check`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Checks if one or more wallets are able to carry a charge_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#chargewalletrequestitem'>ChargeWalletRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Checks if one or more charges would succeed without lacking credits. This will not generate a
transaction message, and as a result, the description will never be used.


### `deposit`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#deposittowalletrequestitem'>DepositToWalletRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rootDeposit`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#rootdepositrequestitem'>RootDepositRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `transfer`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#transfertowalletrequestitem'>TransferToWalletRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAllocation`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Update an existing allocation_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#updateallocationrequestitem'>UpdateAllocationRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Updates one or more existing allocations. This endpoint will use all the provided values. That is,
you must provide all values, even if they do not change. This will generate a transaction indicating
the change. This will set the initial balance of the allocation, as if it was initially created with
this value.

The constraints that are in place during a standard creation are still in place when updating the
values. This means that the new start and end dates _must_ overlap with the values of all ancestors.



## Data Models

### `ChargeWalletRequestItem`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ChargeWalletRequestItem(
    val payer: WalletOwner,
    val units: Long,
    val numberOfProducts: Long,
    val product: ProductReference,
    val performedBy: String,
    val description: String,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>payer</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The payer of this charge
</summary>





</details>

<details>
<summary>
<code>units</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of units that this charge is about
</summary>



The unit itself is defined by the product. The unit can, for example, describe that the 'units' describe the
number of minutes/hours/days.


</details>

<details>
<summary>
<code>numberOfProducts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of products involved in this charge, for example the number of nodes
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which the service is charging for
</summary>





</details>

<details>
<summary>
<code>performedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The username of the user who generated this request
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of the charge this is used purely for presentation purposes
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

### `DepositToWalletRequestItem`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DepositToWalletRequestItem(
    val recipient: WalletOwner,
    val sourceAllocation: String,
    val amount: Long,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>recipient</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The recipient of this deposit
</summary>





</details>

<details>
<summary>
<code>sourceAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the source allocation which the deposit will draw from
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The amount of credits to deposit into the recipient's wallet
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of this change. This is used purely for presentation purposes.
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become valid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
immediately.


</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become invalid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation will never
expire.


</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

### `RootDepositRequestItem`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_See `DepositToWalletRequestItem`_

```kotlin
data class RootDepositRequestItem(
    val categoryId: ProductCategoryId,
    val recipient: WalletOwner,
    val amount: Long,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryId.md'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>recipient</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code>
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `TransferToWalletRequestItem`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TransferToWalletRequestItem(
    val categoryId: ProductCategoryId,
    val target: WalletOwner,
    val source: WalletOwner,
    val amount: Long,
    val startDate: Long?,
    val endDate: Long?,
    val transactionId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>categoryId</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductCategoryId.md'>ProductCategoryId</a></code></code> The category to transfer from
</summary>





</details>

<details>
<summary>
<code>target</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The target wallet to insert the credits into
</summary>





</details>

<details>
<summary>
<code>source</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.WalletOwner.md'>WalletOwner</a></code></code> The source wallet from where the credits is transferred from
</summary>





</details>

<details>
<summary>
<code>amount</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The amount of credits to transfer
</summary>





</details>

<details>
<summary>
<code>startDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become valid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
immediately.


</details>

<details>
<summary>
<code>endDate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this deposit should become invalid
</summary>



This value must overlap with the source allocation. A value of null indicates that the allocation will never
expire.


</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---

### `UpdateAllocationRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdateAllocationRequestItem(
    val id: String,
    val balance: Long,
    val startDate: Long,
    val endDate: Long?,
    val reason: String,
    val transactionId: String?,
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
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
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
<code>reason</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>transactionId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An traceable id for this specific transaction. Used to counter duplicate transactions and to trace cascading transactions
</summary>





</details>



</details>



---


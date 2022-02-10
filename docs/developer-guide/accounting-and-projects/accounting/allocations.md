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
<tr><td><a href='#example-charging-a-leaf-allocation-(differential)'>Charging a leaf allocation (Differential)</a></td></tr>
<tr><td><a href='#example-charging-a-leaf-allocation-with-missing-credits-(absolute)'>Charging a leaf allocation with missing credits (Absolute)</a></td></tr>
<tr><td><a href='#example-charging-a-leaf-allocation-with-missing-credits-(differential)'>Charging a leaf allocation with missing credits (Differential)</a></td></tr>
<tr><td><a href='#example-creating-a-sub-allocation-(deposit-operation)'>Creating a sub-allocation (deposit operation)</a></td></tr>
<tr><td><a href='#example-creating-a-new-root-allocation-(transfer-operation)'>Creating a new root allocation (transfer operation)</a></td></tr>
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
<td>Records usage in the system</td>
</tr>
<tr>
<td><a href='#check'><code>check</code></a></td>
<td>Checks if one or more wallets are able to carry a charge</td>
</tr>
<tr>
<td><a href='#deposit'><code>deposit</code></a></td>
<td>Creates a new sub-allocation from a parent allocation</td>
</tr>
<tr>
<td><a href='#rootdeposit'><code>rootDeposit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#transfer'><code>transfer</code></a></td>
<td>Creates a new root allocation from a parent allocation</td>
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
            grantedIn = 1, 
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
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        periods = 1, 
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
            grantedIn = 1, 
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
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        periods = 1, 
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
            grantedIn = 1, 
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
                    "endDate": null,
                    "grantedIn": 1
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
                "periods": 1,
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
                    "endDate": null,
                    "grantedIn": 1
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
                "periods": 1,
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
                    "endDate": null,
                    "grantedIn": 1
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
#                     "endDate": null,
#                     "grantedIn": 1
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
            "periods": 1,
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
#                     "endDate": null,
#                     "grantedIn": 1
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
            "periods": 1,
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
#                     "endDate": null,
#                     "grantedIn": 1
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

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_charge-absolute-single.png)

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
            grantedIn = 1, 
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
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        periods = 1, 
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
            grantedIn = 1, 
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
        payer = WalletOwner.Project(
            projectId = "my-research", 
        ), 
        performedBy = "user", 
        periods = 1, 
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
            grantedIn = 1, 
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
                    "endDate": null,
                    "grantedIn": 1
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
                "periods": 1,
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
                    "endDate": null,
                    "grantedIn": 1
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
                "periods": 1,
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
                    "endDate": null,
                    "grantedIn": 1
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
#                     "endDate": null,
#                     "grantedIn": 1
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
            "periods": 1,
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
#                     "endDate": null,
#                     "grantedIn": 1
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
            "periods": 1,
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
#                     "endDate": null,
#                     "grantedIn": 1
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

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_charge-differential-single.png)

</details>


## Example: Charging a leaf allocation (Absolute)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The PI of the root project (<code>piRoot</code>)</li>
<li>The PI of the leaf project (<code>piLeaf</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
hierarchy we use consists of a single root allocation. The root allocation has a single child, 
which we will be referring to as the leaf, since it has no children. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 1000, 
            endDate = null, 
            grantedIn = 1, 
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
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 1, 
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

/* As we can see, in our initial state, the root has 1000 core hours remaining and the leaf has 500. */


/* We now perform our charge of a single core hour. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        payer = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        performedBy = "user", 
        periods = 1, 
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

/* The response, as expected, that we had enough credits for the transaction. This would have been 
false if _any_ of the allocation in the hierarchy runs out of credits. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 999, 
            endDate = null, 
            grantedIn = 1, 
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

/* On the root allocation, we see that this has subtracted a single core hour from the balance. Recall 
that balance shows the overall balance for the entire subtree. The local balance of the root 
remains unaffected, since this wasn't consumed by the root.  */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 499, 
            endDate = null, 
            grantedIn = 1, 
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

/* In the leaf allocation, we see that this has affected both the balance and the local balance. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
hierarchy we use consists of a single root allocation. The root allocation has a single child, 
which we will be referring to as the leaf, since it has no children. */

// Authenticated as piRoot
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
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                    "endDate": null,
                    "grantedIn": 1
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

/* As we can see, in our initial state, the root has 1000 core hours remaining and the leaf has 500. */


/* We now perform our charge of a single core hour. */

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
                "periods": 1,
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

/* The response, as expected, that we had enough credits for the transaction. This would have been 
false if _any_ of the allocation in the hierarchy runs out of credits. */

// Authenticated as piRoot
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
                    "endDate": null,
                    "grantedIn": 1
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

/* On the root allocation, we see that this has subtracted a single core hour from the balance. Recall 
that balance shows the overall balance for the entire subtree. The local balance of the root 
remains unaffected, since this wasn't consumed by the root.  */

// Authenticated as piLeaf
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
                    "endDate": null,
                    "grantedIn": 1
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

/* In the leaf allocation, we see that this has affected both the balance and the local balance. */

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

# In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
# hierarchy we use consists of a single root allocation. The root allocation has a single child, 
# which we will be referring to as the leaf, since it has no children.

# Authenticated as piRoot
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
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                     "endDate": null,
#                     "grantedIn": 1
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

# As we can see, in our initial state, the root has 1000 core hours remaining and the leaf has 500.

# We now perform our charge of a single core hour.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "units": 1,
            "periods": 1,
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

# The response, as expected, that we had enough credits for the transaction. This would have been 
# false if _any_ of the allocation in the hierarchy runs out of credits.

# Authenticated as piRoot
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
#                     "endDate": null,
#                     "grantedIn": 1
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

# On the root allocation, we see that this has subtracted a single core hour from the balance. Recall 
# that balance shows the overall balance for the entire subtree. The local balance of the root 
# remains unaffected, since this wasn't consumed by the root. 

# Authenticated as piLeaf
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
#                     "endDate": null,
#                     "grantedIn": 1
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

# In the leaf allocation, we see that this has affected both the balance and the local balance.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_charge-absolute-multi.png)

</details>


## Example: Charging a leaf allocation (Differential)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The PI of the root project (<code>piRoot</code>)</li>
<li>The PI of the leaf project (<code>piLeaf</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
hierarchy we use consists of a single root allocation. The root allocation has a single child, 
which we will be referring to as the leaf, since it has no children. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 1000, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 500, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
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

/* As we can see, in our initial state, the root has 1000 GB remaining and the leaf has 500. */


/* We now perform our charge of 100 GB on the leaf. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        payer = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        performedBy = "user", 
        periods = 1, 
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

/* The response, as expected, that we had enough credits for the transaction. This would have been 
false if _any_ of the allocation in the hierarchy runs out of credits. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 900, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
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

/* On the root allocation, we see that this has subtracted 100 GB from the balance. Recall that 
balance shows the overall balance for the entire subtree. The local balance of the root remains 
unaffected, since this wasn't consumed by the root. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 400, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 400, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
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

/* In the leaf allocation, we see that this has affected both the balance and the local balance.  */


/* We now attempt to perform a similar charge, of 50 GB, but this time on the root allocation. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        payer = WalletOwner.Project(
            projectId = "root-project", 
        ), 
        performedBy = "user", 
        periods = 1, 
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

/* Again, this allocation succeeds. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 850, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 950, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
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

/* This charge has affected the local and current balance of the root by the expected 50 GB. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 400, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 400, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
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

/* The leaf allocation remains unchanged. Any and all charges will only affect the charged allocation 
and their ancestors. A descendant is never directly updated by such an operation. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
hierarchy we use consists of a single root allocation. The root allocation has a single child, 
which we will be referring to as the leaf, since it has no children. */

// Authenticated as piRoot
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
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                "name": "example-storage",
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
                    "endDate": null,
                    "grantedIn": 1
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

/* As we can see, in our initial state, the root has 1000 GB remaining and the leaf has 500. */


/* We now perform our charge of 100 GB on the leaf. */

// Authenticated as ucloud
await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "leaf-project"
                },
                "units": 100,
                "periods": 1,
                "product": {
                    "id": "example-storage",
                    "category": "example-storage",
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

/* The response, as expected, that we had enough credits for the transaction. This would have been 
false if _any_ of the allocation in the hierarchy runs out of credits. */

// Authenticated as piRoot
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
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* On the root allocation, we see that this has subtracted 100 GB from the balance. Recall that 
balance shows the overall balance for the entire subtree. The local balance of the root remains 
unaffected, since this wasn't consumed by the root. */

// Authenticated as piLeaf
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": 400,
                    "initialBalance": 500,
                    "localBalance": 400,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* In the leaf allocation, we see that this has affected both the balance and the local balance.  */


/* We now attempt to perform a similar charge, of 50 GB, but this time on the root allocation. */

// Authenticated as ucloud
await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "root-project"
                },
                "units": 50,
                "periods": 1,
                "product": {
                    "id": "example-storage",
                    "category": "example-storage",
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

/* Again, this allocation succeeds. */

// Authenticated as piRoot
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 850,
                    "initialBalance": 1000,
                    "localBalance": 950,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* This charge has affected the local and current balance of the root by the expected 50 GB. */

// Authenticated as piLeaf
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": 400,
                    "initialBalance": 500,
                    "localBalance": 400,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* The leaf allocation remains unchanged. Any and all charges will only affect the charged allocation 
and their ancestors. A descendant is never directly updated by such an operation. */

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

# In this example, we will show how a charge affects the rest of the allocation hierarchy. The 
# hierarchy we use consists of a single root allocation. The root allocation has a single child, 
# which we will be referring to as the leaf, since it has no children.

# Authenticated as piRoot
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
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                 "name": "example-storage",
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
#                     "endDate": null,
#                     "grantedIn": 1
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

# As we can see, in our initial state, the root has 1000 GB remaining and the leaf has 500.

# We now perform our charge of 100 GB on the leaf.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "units": 100,
            "periods": 1,
            "product": {
                "id": "example-storage",
                "category": "example-storage",
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

# The response, as expected, that we had enough credits for the transaction. This would have been 
# false if _any_ of the allocation in the hierarchy runs out of credits.

# Authenticated as piRoot
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
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# On the root allocation, we see that this has subtracted 100 GB from the balance. Recall that 
# balance shows the overall balance for the entire subtree. The local balance of the root remains 
# unaffected, since this wasn't consumed by the root.

# Authenticated as piLeaf
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": 400,
#                     "initialBalance": 500,
#                     "localBalance": 400,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# In the leaf allocation, we see that this has affected both the balance and the local balance. 

# We now attempt to perform a similar charge, of 50 GB, but this time on the root allocation.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "root-project"
            },
            "units": 50,
            "periods": 1,
            "product": {
                "id": "example-storage",
                "category": "example-storage",
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

# Again, this allocation succeeds.

# Authenticated as piRoot
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 850,
#                     "initialBalance": 1000,
#                     "localBalance": 950,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# This charge has affected the local and current balance of the root by the expected 50 GB.

# Authenticated as piLeaf
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": 400,
#                     "initialBalance": 500,
#                     "localBalance": 400,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# The leaf allocation remains unchanged. Any and all charges will only affect the charged allocation 
# and their ancestors. A descendant is never directly updated by such an operation.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_charge-differential-multi.png)

</details>


## Example: Charging a leaf allocation with missing credits (Absolute)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The PI of the root project (<code>piRoot</code>)</li>
<li>The PI of the node project (child of root) (<code>piNode</code>)</li>
<li>The PI of the leaf project (child of node) (<code>piLeaf</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will show what happens when an allocation is unable to carry the full charge. 
We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
has no children. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 550, 
            endDate = null, 
            grantedIn = 1, 
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
    piNode
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 50, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 100, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "node-project", 
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
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52", "62"), 
            balance = 450, 
            endDate = null, 
            grantedIn = 1, 
            id = "62", 
            initialBalance = 500, 
            localBalance = 450, 
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

/* As we can see from the allocations, they have already been in use. To be concrete, you can reach 
this state by applying a 400 core hour charge on the node and another 50 core hours on the leaf. */


/* We now attempt to perform a charge of 100 core hours on the leaf. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        payer = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        performedBy = "user", 
        periods = 1, 
        product = ProductReference(
            category = "example-slim", 
            id = "example-slim-1", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 100, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(false), 
)
*/

/* Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
before the charge, only has 50 core hours remaining. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 450, 
            endDate = null, 
            grantedIn = 1, 
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
    piNode
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = -50, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 100, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "node-project", 
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
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52", "62"), 
            balance = 350, 
            endDate = null, 
            grantedIn = 1, 
            id = "62", 
            initialBalance = 500, 
            localBalance = 350, 
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

/* When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
negatives. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show what happens when an allocation is unable to carry the full charge. 
We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
has no children. */

// Authenticated as piRoot
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
                    "balance": 550,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piNode
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
                "projectId": "node-project"
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
                    "balance": 50,
                    "initialBalance": 500,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                    "id": "62",
                    "allocationPath": [
                        "42",
                        "52",
                        "62"
                    ],
                    "balance": 450,
                    "initialBalance": 500,
                    "localBalance": 450,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* As we can see from the allocations, they have already been in use. To be concrete, you can reach 
this state by applying a 400 core hour charge on the node and another 50 core hours on the leaf. */


/* We now attempt to perform a charge of 100 core hours on the leaf. */

// Authenticated as ucloud
await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "leaf-project"
                },
                "units": 100,
                "periods": 1,
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
        false
    ]
}
*/

/* Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
before the charge, only has 50 core hours remaining. */

// Authenticated as piRoot
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
                    "balance": 450,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piNode
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
                "projectId": "node-project"
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
                    "balance": -50,
                    "initialBalance": 500,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                    "id": "62",
                    "allocationPath": [
                        "42",
                        "52",
                        "62"
                    ],
                    "balance": 350,
                    "initialBalance": 500,
                    "localBalance": 350,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
negatives. */

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

# In this example, we will show what happens when an allocation is unable to carry the full charge. 
# We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
# single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
# has no children.

# Authenticated as piRoot
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
#                     "balance": 550,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piNode
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "node-project"
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
#                     "balance": 50,
#                     "initialBalance": 500,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                     "id": "62",
#                     "allocationPath": [
#                         "42",
#                         "52",
#                         "62"
#                     ],
#                     "balance": 450,
#                     "initialBalance": 500,
#                     "localBalance": 450,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# As we can see from the allocations, they have already been in use. To be concrete, you can reach 
# this state by applying a 400 core hour charge on the node and another 50 core hours on the leaf.

# We now attempt to perform a charge of 100 core hours on the leaf.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "units": 100,
            "periods": 1,
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
#         false
#     ]
# }

# Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
# before the charge, only has 50 core hours remaining.

# Authenticated as piRoot
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
#                     "balance": 450,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piNode
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "node-project"
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
#                     "balance": -50,
#                     "initialBalance": 500,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                     "id": "62",
#                     "allocationPath": [
#                         "42",
#                         "52",
#                         "62"
#                     ],
#                     "balance": 350,
#                     "initialBalance": 500,
#                     "localBalance": 350,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
# balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
# negatives.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_charge-absolute-multi-missing.png)

</details>


## Example: Charging a leaf allocation with missing credits (Differential)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The PI of the root project (<code>piRoot</code>)</li>
<li>The PI of the node project (child of root) (<code>piNode</code>)</li>
<li>The PI of the leaf project (child of node) (<code>piLeaf</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will show what happens when an allocation is unable to carry the full charge. 
We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
has no children. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 550, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piNode
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 50, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 100, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "node-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52", "62"), 
            balance = 450, 
            endDate = null, 
            grantedIn = 1, 
            id = "62", 
            initialBalance = 500, 
            localBalance = 450, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
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

/* As we can see from the allocations, they have already been in use. To be concrete, you can reach 
this state by applying a 400 GB charge on the node and another 50 GB on the leaf. */


/* We now attempt to perform a charge of 110 GB on the leaf. */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        payer = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        performedBy = "user", 
        periods = 1, 
        product = ProductReference(
            category = "example-storage", 
            id = "example-storage", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 110, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(false), 
)
*/

/* Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
before the charge, only has 50 GB remaining. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 490, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piNode
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = -10, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 100, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "node-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52", "62"), 
            balance = 390, 
            endDate = null, 
            grantedIn = 1, 
            id = "62", 
            initialBalance = 500, 
            localBalance = 390, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
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

/* When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
negatives. */


/* We now assume that the leaf deletes all their data. The accounting system records this as a charge 
for 0 units (GB). */

Accounting.charge.call(
    bulkRequestOf(ChargeWalletRequestItem(
        description = "A charge for compute usage", 
        payer = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        performedBy = "user", 
        periods = 1, 
        product = ProductReference(
            category = "example-storage", 
            id = "example-storage", 
            provider = "example", 
        ), 
        transactionId = "charge-1", 
        units = 0, 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(true), 
)
*/

/* This charge succeeds, as it is bringing the balance back into the positive. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 490, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 1000, 
            localBalance = 1000, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "root-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 100, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 500, 
            localBalance = 100, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "node-project", 
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
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52", "62"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 1, 
            id = "62", 
            initialBalance = 500, 
            localBalance = 500, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        owner = WalletOwner.Project(
            projectId = "leaf-project", 
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
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show what happens when an allocation is unable to carry the full charge. 
We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
has no children. */

// Authenticated as piRoot
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 550,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piNode
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
                "projectId": "node-project"
            },
            "paysFor": {
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": 50,
                    "initialBalance": 500,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "62",
                    "allocationPath": [
                        "42",
                        "52",
                        "62"
                    ],
                    "balance": 450,
                    "initialBalance": 500,
                    "localBalance": 450,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* As we can see from the allocations, they have already been in use. To be concrete, you can reach 
this state by applying a 400 GB charge on the node and another 50 GB on the leaf. */


/* We now attempt to perform a charge of 110 GB on the leaf. */

// Authenticated as ucloud
await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "leaf-project"
                },
                "units": 110,
                "periods": 1,
                "product": {
                    "id": "example-storage",
                    "category": "example-storage",
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
        false
    ]
}
*/

/* Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
before the charge, only has 50 GB remaining. */

// Authenticated as piRoot
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 490,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piNode
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
                "projectId": "node-project"
            },
            "paysFor": {
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": -10,
                    "initialBalance": 500,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "62",
                    "allocationPath": [
                        "42",
                        "52",
                        "62"
                    ],
                    "balance": 390,
                    "initialBalance": 500,
                    "localBalance": 390,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
negatives. */


/* We now assume that the leaf deletes all their data. The accounting system records this as a charge 
for 0 units (GB). */

// Authenticated as ucloud
await callAPI(AccountingApi.charge(
    {
        "items": [
            {
                "payer": {
                    "type": "project",
                    "projectId": "leaf-project"
                },
                "units": 0,
                "periods": 1,
                "product": {
                    "id": "example-storage",
                    "category": "example-storage",
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

/* This charge succeeds, as it is bringing the balance back into the positive. */

// Authenticated as piRoot
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "42",
                    "allocationPath": [
                        "42"
                    ],
                    "balance": 490,
                    "initialBalance": 1000,
                    "localBalance": 1000,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                "projectId": "node-project"
            },
            "paysFor": {
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "42",
                        "52"
                    ],
                    "balance": 100,
                    "initialBalance": 500,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
                "name": "example-storage",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "62",
                    "allocationPath": [
                        "42",
                        "52",
                        "62"
                    ],
                    "balance": 500,
                    "initialBalance": 500,
                    "localBalance": 500,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

# In this example, we will show what happens when an allocation is unable to carry the full charge. 
# We will be using a more complex hierarchy. The hierarchy will have a single root. The root has a 
# single child, the 'node' allocation. This node has a single child allocation, the leaf. The leaf 
# has no children.

# Authenticated as piRoot
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 550,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piNode
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "node-project"
#             },
#             "paysFor": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": 50,
#                     "initialBalance": 500,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "62",
#                     "allocationPath": [
#                         "42",
#                         "52",
#                         "62"
#                     ],
#                     "balance": 450,
#                     "initialBalance": 500,
#                     "localBalance": 450,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# As we can see from the allocations, they have already been in use. To be concrete, you can reach 
# this state by applying a 400 GB charge on the node and another 50 GB on the leaf.

# We now attempt to perform a charge of 110 GB on the leaf.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "units": 110,
            "periods": 1,
            "product": {
                "id": "example-storage",
                "category": "example-storage",
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
#         false
#     ]
# }

# Even though the leaf, seen in isolation, has enough credits. The failure occurs in the node which, 
# before the charge, only has 50 GB remaining.

# Authenticated as piRoot
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 490,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piNode
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "node-project"
#             },
#             "paysFor": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": -10,
#                     "initialBalance": 500,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "62",
#                     "allocationPath": [
#                         "42",
#                         "52",
#                         "62"
#                     ],
#                     "balance": 390,
#                     "initialBalance": 500,
#                     "localBalance": 390,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# When we apply the charge, the node reaches a negative balance. If any allocation reaches a negative 
# balance, then the charge has failed. As we can see, it is possible for a balance to go into the 
# negatives.

# We now assume that the leaf deletes all their data. The accounting system records this as a charge 
# for 0 units (GB).

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/charge" -d '{
    "items": [
        {
            "payer": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "units": 0,
            "periods": 1,
            "product": {
                "id": "example-storage",
                "category": "example-storage",
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

# This charge succeeds, as it is bringing the balance back into the positive.

# Authenticated as piRoot
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "42",
#                     "allocationPath": [
#                         "42"
#                     ],
#                     "balance": 490,
#                     "initialBalance": 1000,
#                     "localBalance": 1000,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "node-project"
#             },
#             "paysFor": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "42",
#                         "52"
#                     ],
#                     "balance": 100,
#                     "initialBalance": 500,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "62",
#                     "allocationPath": [
#                         "42",
#                         "52",
#                         "62"
#                     ],
#                     "balance": 500,
#                     "initialBalance": 500,
#                     "localBalance": 500,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_charge-differential-multi-missing.png)

</details>


## Example: Creating a sub-allocation (deposit operation)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The PI of the root project (<code>piRoot</code>)</li>
<li>The PI of the leaf project (child of root) (<code>piLeaf</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will show how a workspace can create a sub-allocation. The new allocation will 
have an existing allocation as a child. This is the recommended way of creating allocations. 
Resources are not immediately removed from the parent allocation. In addition, workspaces can 
over-allocate resources. For example, a workspace can deposit more resources than they have into 
sub-allocations. This doesn't create more resources in the system. As we saw from the charge 
examples, all allocations in a hierarchy must be able to carry a charge. */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 500, 
            localBalance = 500, 
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
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = emptyList(), 
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

/* Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
resources at the moment. */


/* We now perform a deposit operation with the leaf workspace as the target. */

Accounting.deposit.call(
    bulkRequestOf(DepositToWalletRequestItem(
        amount = 100, 
        description = "Create sub-allocation", 
        dry = false, 
        endDate = null, 
        recipient = WalletOwner.Project(
            projectId = "leaf-project", 
        ), 
        sourceAllocation = "42", 
        startDate = null, 
        transactionId = "-49243634961058074201644493808561", 
    )),
    piRoot
).orThrow()

/*
Unit
*/
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 500, 
            localBalance = 500, 
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
    piLeaf
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42", "52"), 
            balance = 100, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 100, 
            localBalance = 100, 
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

/* After inspecting the allocations, we see that the original (root) allocation remains unchanged. 
However, the leaf workspace now have a new allocation. This allocation has the root allocation as a 
parent, indicated by the path.  */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how a workspace can create a sub-allocation. The new allocation will 
have an existing allocation as a child. This is the recommended way of creating allocations. 
Resources are not immediately removed from the parent allocation. In addition, workspaces can 
over-allocate resources. For example, a workspace can deposit more resources than they have into 
sub-allocations. This doesn't create more resources in the system. As we saw from the charge 
examples, all allocations in a hierarchy must be able to carry a charge. */

// Authenticated as piRoot
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
                    "balance": 500,
                    "initialBalance": 500,
                    "localBalance": 500,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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

/* Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
resources at the moment. */


/* We now perform a deposit operation with the leaf workspace as the target. */

// Authenticated as piRoot
await callAPI(AccountingApi.deposit(
    {
        "items": [
            {
                "recipient": {
                    "type": "project",
                    "projectId": "leaf-project"
                },
                "sourceAllocation": "42",
                "amount": 100,
                "description": "Create sub-allocation",
                "startDate": null,
                "endDate": null,
                "transactionId": "-49243634961058074201644493808561",
                "dry": false
            }
        ]
    }
);

/*
{
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
                    "balance": 500,
                    "initialBalance": 500,
                    "localBalance": 500,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piLeaf
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
                    "balance": 100,
                    "initialBalance": 100,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* After inspecting the allocations, we see that the original (root) allocation remains unchanged. 
However, the leaf workspace now have a new allocation. This allocation has the root allocation as a 
parent, indicated by the path.  */

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

# In this example, we will show how a workspace can create a sub-allocation. The new allocation will 
# have an existing allocation as a child. This is the recommended way of creating allocations. 
# Resources are not immediately removed from the parent allocation. In addition, workspaces can 
# over-allocate resources. For example, a workspace can deposit more resources than they have into 
# sub-allocations. This doesn't create more resources in the system. As we saw from the charge 
# examples, all allocations in a hierarchy must be able to carry a charge.

# Authenticated as piRoot
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
#                     "balance": 500,
#                     "initialBalance": 500,
#                     "localBalance": 500,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
# resources at the moment.

# We now perform a deposit operation with the leaf workspace as the target.

# Authenticated as piRoot
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/deposit" -d '{
    "items": [
        {
            "recipient": {
                "type": "project",
                "projectId": "leaf-project"
            },
            "sourceAllocation": "42",
            "amount": 100,
            "description": "Create sub-allocation",
            "startDate": null,
            "endDate": null,
            "transactionId": "-49243634961058074201644493808561",
            "dry": false
        }
    ]
}'


# {
# }

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
#                     "balance": 500,
#                     "initialBalance": 500,
#                     "localBalance": 500,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piLeaf
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
#                     "balance": 100,
#                     "initialBalance": 100,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# After inspecting the allocations, we see that the original (root) allocation remains unchanged. 
# However, the leaf workspace now have a new allocation. This allocation has the root allocation as a 
# parent, indicated by the path. 

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_deposit.png)

</details>


## Example: Creating a new root allocation (transfer operation)
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The PI of the root project (<code>piRoot</code>)</li>
<li>The PI of the new root project (<code>piSecondRoot</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will show how a workspace can transfer money to another workspace. This is not 
the recommended way of creating granting resources. This approach immediately removes all resources 
from the parent. The parent cannot observe usage from the child. In addition, the workspace is not 
allowed to over-allocate resources. We recommend using deposit for almost all cases. Workspace PIs 
should only use transfers if they wish to give away resources that they otherwise will not be able 
to consume.  */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 500, 
            localBalance = 500, 
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
    piSecondRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = emptyList(), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "second-root-project", 
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

/* Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
resources at the moment. */


/* We now perform a transfer operation with the leaf workspace as the target. */

Accounting.transfer.call(
    bulkRequestOf(TransferToWalletRequestItem(
        amount = 100, 
        categoryId = ProductCategoryId(
            id = "example-slim", 
            name = "example-slim", 
            provider = "example", 
        ), 
        dry = false, 
        endDate = null, 
        source = WalletOwner.Project(
            projectId = "root-project", 
        ), 
        startDate = null, 
        target = WalletOwner.Project(
            projectId = "second-root-project", 
        ), 
        transactionId = "13951699255060081561644493808564", 
    )),
    piRoot
).orThrow()

/*
Unit
*/
Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    piRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("42"), 
            balance = 400, 
            endDate = null, 
            grantedIn = 1, 
            id = "42", 
            initialBalance = 500, 
            localBalance = 400, 
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
    piSecondRoot
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("52"), 
            balance = 100, 
            endDate = null, 
            grantedIn = 1, 
            id = "52", 
            initialBalance = 100, 
            localBalance = 100, 
            startDate = 1633941615074, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.Project(
            projectId = "second-root-project", 
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

/* After inspecting the allocations, we see that the original (root) allocation has changed. The 
system has immediately removed all the resources. The leaf workspace now have a new allocation. 
The new allocation does not have a parent. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how a workspace can transfer money to another workspace. This is not 
the recommended way of creating granting resources. This approach immediately removes all resources 
from the parent. The parent cannot observe usage from the child. In addition, the workspace is not 
allowed to over-allocate resources. We recommend using deposit for almost all cases. Workspace PIs 
should only use transfers if they wish to give away resources that they otherwise will not be able 
to consume.  */

// Authenticated as piRoot
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
                    "balance": 500,
                    "initialBalance": 500,
                    "localBalance": 500,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piSecondRoot
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
                "projectId": "second-root-project"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
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

/* Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
resources at the moment. */


/* We now perform a transfer operation with the leaf workspace as the target. */

// Authenticated as piRoot
await callAPI(AccountingApi.transfer(
    {
        "items": [
            {
                "categoryId": {
                    "name": "example-slim",
                    "provider": "example"
                },
                "target": {
                    "type": "project",
                    "projectId": "second-root-project"
                },
                "source": {
                    "type": "project",
                    "projectId": "root-project"
                },
                "amount": 100,
                "startDate": null,
                "endDate": null,
                "transactionId": "13951699255060081561644493808564",
                "dry": false
            }
        ]
    }
);

/*
{
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
                    "balance": 400,
                    "initialBalance": 500,
                    "localBalance": 400,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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
// Authenticated as piSecondRoot
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
                "projectId": "second-root-project"
            },
            "paysFor": {
                "name": "example-slim",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "52",
                    "allocationPath": [
                        "52"
                    ],
                    "balance": 100,
                    "initialBalance": 100,
                    "localBalance": 100,
                    "startDate": 1633941615074,
                    "endDate": null,
                    "grantedIn": 1
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

/* After inspecting the allocations, we see that the original (root) allocation has changed. The 
system has immediately removed all the resources. The leaf workspace now have a new allocation. 
The new allocation does not have a parent. */

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

# In this example, we will show how a workspace can transfer money to another workspace. This is not 
# the recommended way of creating granting resources. This approach immediately removes all resources 
# from the parent. The parent cannot observe usage from the child. In addition, the workspace is not 
# allowed to over-allocate resources. We recommend using deposit for almost all cases. Workspace PIs 
# should only use transfers if they wish to give away resources that they otherwise will not be able 
# to consume. 

# Authenticated as piRoot
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
#                     "balance": 500,
#                     "initialBalance": 500,
#                     "localBalance": 500,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piSecondRoot
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "second-root-project"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "UNITS_PER_HOUR"
#         }
#     ],
#     "next": null
# }

# Our initial state shows that the root project has 500 core hours. The leaf doesn't have any 
# resources at the moment.

# We now perform a transfer operation with the leaf workspace as the target.

# Authenticated as piRoot
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/accounting/transfer" -d '{
    "items": [
        {
            "categoryId": {
                "name": "example-slim",
                "provider": "example"
            },
            "target": {
                "type": "project",
                "projectId": "second-root-project"
            },
            "source": {
                "type": "project",
                "projectId": "root-project"
            },
            "amount": 100,
            "startDate": null,
            "endDate": null,
            "transactionId": "13951699255060081561644493808564",
            "dry": false
        }
    ]
}'


# {
# }

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
#                     "balance": 400,
#                     "initialBalance": 500,
#                     "localBalance": 400,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# Authenticated as piSecondRoot
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "project",
#                 "projectId": "second-root-project"
#             },
#             "paysFor": {
#                 "name": "example-slim",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "52",
#                     "allocationPath": [
#                         "52"
#                     ],
#                     "balance": 100,
#                     "initialBalance": 100,
#                     "localBalance": 100,
#                     "startDate": 1633941615074,
#                     "endDate": null,
#                     "grantedIn": 1
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

# After inspecting the allocations, we see that the original (root) allocation has changed. The 
# system has immediately removed all the resources. The leaf workspace now have a new allocation. 
# The new allocation does not have a parent.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/accounting_transfer.png)

</details>



## Remote Procedure Calls

### `charge`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Records usage in the system_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#chargewalletrequestitem'>ChargeWalletRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Internal UCloud services invoke this endpoint to record usage from a workspace. Providers report data 
indirectly to this API through the outgoing `Control` API. This endpoint causes changes in the balances 
of the targeted allocation and ancestors. UCloud will change the `balance` and `localBalance` property 
of the targeted allocation. Ancestors of the targeted allocation will only update their `balance`.

UCloud returns a boolean, for every request, indicating if the charge was successful. A charge is 
successful if no affected allocation went into a negative balance.

---

__📝 NOTE:__ Unsuccessful charges are still deducted in their balances.

---

The semantics of `charge` depends on the Product's payment model.

__Absolute:__

- UCloud calculates the change in balances by multiplying: the Product's pricePerUnit, the number of 
  units, the number of periods
- UCloud subtracts this change from the balances

__Differential:__

- UCloud calculates the change in balances by comparing the units with the current `localBalance`
- UCloud subtracts this change from the balances
- Note: This change can cause the balance to go up, if the usage is lower than last period

#### Selecting Allocations

The charge operation targets a wallet (by combining the ProductCategoryId and WalletOwner). This means 
that the charge operation have multiple allocations to consider. We explain the approach for absolute 
payment models. The approach is similar for differential products.

UCloud first finds a set of leaf allocations which, when combined, can carry the full change. UCloud 
first finds a set of candidates. We do this by sorting allocations by the Wallet's `chargePolicy`. By 
default, this means that UCloud prioritizes allocations that expire soon. UCloud only considers 
allocations which are active and have a positive balance.

---

__📝 NOTE:__ UCloud does not consider ancestors at this point in the process.

---

UCloud now creates the list of allocations which it will use. We do this by performing a rolling sum of 
the balances. UCloud adds an allocation to the set if the rolling sum has not yet reached the total 
amount.

UCloud will use the full balance of each selected allocation. The only exception is the last element, 
which might use less. If the change in balance is never reached, then UCloud will further charge the 
first selected allocation. In this case, the priority allocation will have to pay the difference.

Finally, the system updates the balances of each selected leaf, and all of their ancestors.

__Examples:__

| Example |
|---------|
| [Charging a root allocation (Absolute)](/docs/reference/accounting_charge-absolute-single.md) |
| [Charging a leaf allocation (Absolute)](/docs/reference/accounting_charge-absolute-multi.md) |
| [Charging a leaf allocation with missing credits (Absolute)](/docs/reference/accounting_charge-absolute-multi-missing.md) |
| [Charging a root allocation (Differential)](/docs/reference/accounting_charge-differential-single.md) |
| [Charging a leaf allocation (Differential)](/docs/reference/accounting_charge-differential-multi.md) |
| [Charging a leaf allocation with missing credits (Differential)](/docs/reference/accounting_charge-differential-multi-missing.md) |


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


_Creates a new sub-allocation from a parent allocation_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#deposittowalletrequestitem'>DepositToWalletRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The new allocation will have the current allocation as a parent. The balance of the parent allocation 
is not changed.

__Examples:__

| Example |
|---------|
| [Creating a sub-allocation](/docs/reference/accounting_deposit.md) |


### `rootDeposit`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#rootdepositrequestitem'>RootDepositRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `transfer`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new root allocation from a parent allocation_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#transfertowalletrequestitem'>TransferToWalletRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The new allocation will have no parents. The balance of the parent allocation is immediately removed, 
in full.

__Examples:__

| Example |
|---------|
| [Creating a new root allocation](/docs/reference/accounting_transfer.md) |


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
    val periods: Long,
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
<code>periods</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The number of products involved in this charge, for example the number of nodes
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
    val dry: Boolean?,
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

<details>
<summary>
<code>dry</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
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
    val providerGeneratedId: String?,
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

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
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
    val dry: Boolean?,
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

<details>
<summary>
<code>dry</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
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


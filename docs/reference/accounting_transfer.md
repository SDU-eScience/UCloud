[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# Example: Creating a new root allocation (transfer operation)

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
        transactionId = "-13807957120350944841644846940028", 
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
                "transactionId": "-13807957120350944841644846940028",
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
            "transactionId": "-13807957120350944841644846940028",
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



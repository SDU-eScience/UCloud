[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# Example: Creating a sub-allocation (deposit operation)

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
        transactionId = "89311623230733601381644501366000", 
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
                "transactionId": "89311623230733601381644501366000",
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
            "transactionId": "89311623230733601381644501366000",
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



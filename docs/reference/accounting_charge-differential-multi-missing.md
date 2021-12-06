[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# Example: Charging a leaf allocation with missing credits (Differential)

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



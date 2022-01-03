[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# Example: Charging a leaf allocation (Absolute)

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



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# Example: Charging a leaf allocation (Absolute)

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



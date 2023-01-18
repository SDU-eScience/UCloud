[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Accounting](/docs/developer-guide/accounting-and-projects/accounting/README.md) / [Accounting Operations](/docs/developer-guide/accounting-and-projects/accounting/allocations.md)

# Example: Charging a root allocation (Differential)

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
            allowSubAllocationsToAllocate = true, 
            balance = 1000, 
            canAllocate = false, 
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
            allowSubAllocationsToAllocate = true, 
            balance = 900, 
            canAllocate = false, 
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
            allowSubAllocationsToAllocate = true, 
            balance = 950, 
            canAllocate = false, 
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
#                     "grantedIn": 1,
#                     "canAllocate": false,
#                     "allowSubAllocationsToAllocate": true
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
#                     "grantedIn": 1,
#                     "canAllocate": false,
#                     "allowSubAllocationsToAllocate": true
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
#                     "grantedIn": 1,
#                     "canAllocate": false,
#                     "allowSubAllocationsToAllocate": true
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



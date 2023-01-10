[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Retrieving a list of products supported by accessible providers

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>Typically triggered by a client to determine which operations are supported</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>The user has access to the 'ucloud' provider</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Files.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("ucloud" to listOf(ResolvedSupport(
        product = Product.Storage(
            category = ProductCategoryId(
                id = "u1-cephfs", 
                name = "u1-cephfs", 
                provider = "ucloud", 
            ), 
            chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
            description = "Storage provided by UCloud", 
            freeToUse = false, 
            hiddenInGrantApplications = false, 
            name = "u1-cephfs", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.STORAGE, 
            unitOfPrice = ProductPriceUnit.PER_UNIT, 
            version = 1, 
            balance = null, 
            id = "u1-cephfs", 
        ), 
        support = FSSupport(
            collection = FSCollectionSupport(
                aclModifiable = false, 
                usersCanCreate = true, 
                usersCanDelete = true, 
                usersCanRename = true, 
            ), 
            files = FSFileSupport(
                aclModifiable = false, 
                isReadOnly = false, 
                searchSupported = true, 
                streamingSearchSupported = false, 
                trashSupported = true, 
            ), 
            product = ProductReference(
                category = "u1-cephfs", 
                id = "u1-cephfs", 
                provider = "ucloud", 
            ), 
            stats = FSProductStatsSupport(
                accessedAt = false, 
                createdAt = true, 
                modifiedAt = true, 
                sizeInBytes = true, 
                sizeIncludingChildrenInBytes = true, 
                unixGroup = true, 
                unixOwner = true, 
                unixPermissions = true, 
            ), 
        ), 
    ))), 
)
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

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/retrieveProducts" 

# {
#     "productsByProvider": {
#         "ucloud": [
#             {
#                 "product": {
#                     "balance": null,
#                     "name": "u1-cephfs",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "u1-cephfs",
#                         "provider": "ucloud"
#                     },
#                     "description": "Storage provided by UCloud",
#                     "priority": 0,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "PER_UNIT",
#                     "chargeType": "DIFFERENTIAL_QUOTA",
#                     "hiddenInGrantApplications": false,
#                     "productType": "STORAGE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "u1-cephfs",
#                         "category": "u1-cephfs",
#                         "provider": "ucloud"
#                     },
#                     "stats": {
#                         "sizeInBytes": true,
#                         "sizeIncludingChildrenInBytes": true,
#                         "modifiedAt": true,
#                         "createdAt": true,
#                         "accessedAt": false,
#                         "unixPermissions": true,
#                         "unixOwner": true,
#                         "unixGroup": true
#                     },
#                     "collection": {
#                         "aclModifiable": false,
#                         "usersCanCreate": true,
#                         "usersCanDelete": true,
#                         "usersCanRename": true
#                     },
#                     "files": {
#                         "aclModifiable": false,
#                         "trashSupported": true,
#                         "isReadOnly": false,
#                         "searchSupported": true,
#                         "streamingSearchSupported": false
#                     }
#                 }
#             }
#         ]
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_retrieve_products.png)

</details>



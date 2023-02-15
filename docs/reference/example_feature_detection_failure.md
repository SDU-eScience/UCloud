[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# Example: Feature detection (Failure scenario)

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
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

/* In this example, we will show how to use the feature detection feature of resources. Recall, that
providers need to specify if they support counting backwards. */

Resources.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.Compute(
            category = ProductCategoryId(
                id = "example-compute", 
                name = "example-compute", 
                provider = "example", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            cpu = 1, 
            cpuModel = null, 
            description = "An example machine", 
            freeToUse = false, 
            gpu = null, 
            gpuModel = null, 
            hiddenInGrantApplications = false, 
            memoryInGigs = 1, 
            memoryModel = null, 
            name = "example-compute", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.COMPUTE, 
            unitOfPrice = ProductPriceUnit.UNITS_PER_HOUR, 
            version = 1, 
            balance = null, 
            id = "example-compute", 
        ), 
        support = ExampleResourceSupport(
            maintenance = null, 
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            supportsBackwardsCounting = Supported.NOT_SUPPORTED, 
        ), 
    ))), 
)
*/

/* In this case, the provider does not support counting backwards. */


/* Creating a resource which counts backwards should fail. */

Resources.create.call(
    bulkRequestOf(ExampleResource.Spec(
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        start = 0, 
        target = -100, 
    )),
    user
).orThrow()

/*
400 Bad Request
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

# In this example, we will show how to use the feature detection feature of resources. Recall, that
# providers need to specify if they support counting backwards.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "type": "compute",
#                     "balance": null,
#                     "name": "example-compute",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-compute",
#                         "provider": "example"
#                     },
#                     "description": "An example machine",
#                     "priority": 0,
#                     "cpu": 1,
#                     "memoryInGigs": 1,
#                     "gpu": null,
#                     "cpuModel": null,
#                     "memoryModel": null,
#                     "gpuModel": null,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "UNITS_PER_HOUR",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "COMPUTE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "example-compute",
#                         "category": "example-compute",
#                         "provider": "example"
#                     },
#                     "supportsBackwardsCounting": "NOT_SUPPORTED",
#                     "maintenance": null
#                 }
#             }
#         ]
#     }
# }

# In this case, the provider does not support counting backwards.

# Creating a resource which counts backwards should fail.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example" -d '{
    "items": [
        {
            "start": 0,
            "target": -100,
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            }
        }
    ]
}'


# 400 Bad Request

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example_feature_detection_failure.png)

</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Introduction to Resources API for Providers](/docs/developer-guide/orchestration/storage/providers/resources.md)

# Example: Dealing with partial failures

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will discover how a provider should deal with a partial failure. */

ResourceProvider.create.call(
    bulkRequestOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1234", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "1234", 
    ), ExampleResource(
        createdAt = 1635170395571, 
        id = "51214", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "51214", 
    )),
    ucloud
).orThrow()

/*
500 Internal Server Error
*/

/* In this case, imagine that the provider failed to create the second resource. This should
immediately trigger cleanup on the provider, if the first resource was already created. The provider
should then respond with an appropriate error message. Providers should not attempt to only
partially create the resources. */

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

# In this example, we will discover how a provider should deal with a partial failure.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/example" -d '{
    "items": [
        {
            "id": "1234",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        },
        {
            "id": "51214",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        }
    ]
}'


# 500 Internal Server Error

# In this case, imagine that the provider failed to create the second resource. This should
# immediately trigger cleanup on the provider, if the first resource was already created. The provider
# should then respond with an appropriate error message. Providers should not attempt to only
# partially create the resources.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example.provider.PROVIDERID_create_failure.png)

</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Using licensed software

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>User has already been granted credits for the license (typically through Grants)</li>
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

/* In this example, the user will run a piece of licensed software. */


/* First, the user must activate a copy of their license, which has previously been granted to them through the Grant system. */

Licenses.create.call(
    bulkRequestOf(LicenseSpecification(
        product = ProductReference(
            category = "example-license", 
            id = "example-license", 
            provider = "example", 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "56231", 
    )), 
)
*/

/* This license can now freely be used in Jobs */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-licensed-software", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = mapOf("license" to AppParameterValue.License(
            id = "56231", 
        )), 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = null, 
        restartOnExit = null, 
        sshEnabled = null, 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "55123", 
    )), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, the user will run a piece of licensed software. */


/* First, the user must activate a copy of their license, which has previously been granted to them through the Grant system. */

// Authenticated as user
await callAPI(LicensesApi.create(
    {
        "items": [
            {
                "product": {
                    "id": "example-license",
                    "category": "example-license",
                    "provider": "example"
                }
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "56231"
        }
    ]
}
*/

/* This license can now freely be used in Jobs */

await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-licensed-software",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": null,
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": {
                    "license": {
                        "type": "license_server",
                        "id": "56231"
                    }
                },
                "resources": null,
                "timeAllocation": null,
                "openedFile": null,
                "restartOnExit": null,
                "sshEnabled": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "55123"
        }
    ]
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

# In this example, the user will run a piece of licensed software.

# First, the user must activate a copy of their license, which has previously been granted to them through the Grant system.

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/licenses" -d '{
    "items": [
        {
            "product": {
                "id": "example-license",
                "category": "example-license",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "56231"
#         }
#     ]
# }

# This license can now freely be used in Jobs

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-licensed-software",
                "version": "1.0.0"
            },
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            },
            "name": null,
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": {
                "license": {
                    "type": "license_server",
                    "id": "56231"
                }
            },
            "resources": null,
            "timeAllocation": null,
            "openedFile": null,
            "restartOnExit": null,
            "sshEnabled": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "55123"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_license.png)

</details>



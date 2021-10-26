[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Connecting two Jobs together

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
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

/* In this example our user wish to deploy a simple web application which connects to a database server */


/* The user first provision a database server using an Application */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-database", 
            version = "1.0.0", 
        ), 
        name = "my-database", 
        parameters = mapOf("dataStore" to AppParameterValue.File(
            path = "/123/acme-database", 
            readOnly = false, 
        )), 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = null, 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "4101", 
    )), 
)
*/

/* The database is now `RUNNING` with the persistent from `/123/acme-database` */


/* By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
can be updated by connecting one or more Jobs together. We will now do this using the Application.
"Peer" feature. This feature is commonly referred to as "Connect to Job". */


/* We will now start our web-application and connect it to our existing database Job */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-web-app", 
            version = "1.0.0", 
        ), 
        name = "my-web-app", 
        parameters = null, 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = listOf(AppParameterValue.Peer(
            hostname = "database", 
            jobId = "4101", 
        )), 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "4150", 
    )), 
)
*/

/* The web-application can now connect to the database using the 'database' hostname, as specified in
the JobSpecification. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example our user wish to deploy a simple web application which connects to a database server */


/* The user first provision a database server using an Application */

// Authenticated as user
await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-database",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": "my-database",
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": {
                    "dataStore": {
                        "type": "file",
                        "path": "/123/acme-database",
                        "readOnly": false
                    }
                },
                "resources": null,
                "timeAllocation": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "4101"
        }
    ]
}
*/

/* The database is now `RUNNING` with the persistent from `/123/acme-database` */


/* By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
can be updated by connecting one or more Jobs together. We will now do this using the Application.
"Peer" feature. This feature is commonly referred to as "Connect to Job". */


/* We will now start our web-application and connect it to our existing database Job */

await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-web-app",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": "my-web-app",
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": null,
                "resources": [
                    {
                        "type": "peer",
                        "hostname": "database",
                        "jobId": "4101"
                    }
                ],
                "timeAllocation": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "4150"
        }
    ]
}
*/

/* The web-application can now connect to the database using the 'database' hostname, as specified in
the JobSpecification. */

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

# In this example our user wish to deploy a simple web application which connects to a database server

# The user first provision a database server using an Application

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-database",
                "version": "1.0.0"
            },
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            },
            "name": "my-database",
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": {
                "dataStore": {
                    "type": "file",
                    "path": "/123/acme-database",
                    "readOnly": false
                }
            },
            "resources": null,
            "timeAllocation": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "4101"
#         }
#     ]
# }

# The database is now `RUNNING` with the persistent from `/123/acme-database`

# By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
# can be updated by connecting one or more Jobs together. We will now do this using the Application.
# "Peer" feature. This feature is commonly referred to as "Connect to Job".

# We will now start our web-application and connect it to our existing database Job

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-web-app",
                "version": "1.0.0"
            },
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            },
            "name": "my-web-app",
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": null,
            "resources": [
                {
                    "type": "peer",
                    "hostname": "database",
                    "jobId": "4101"
                }
            ],
            "timeAllocation": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "4150"
#         }
#     ]
# }

# The web-application can now connect to the database using the 'database' hostname, as specified in
# the JobSpecification.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_peers.png)

</details>



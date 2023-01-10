[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Using a web Application

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

/* In this example, the user will create a Job which uses an Application that exposes a web interface */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-web-application", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = null, 
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
        id = "62342", 
    )), 
)
*/
Jobs.openInteractiveSession.call(
    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(
        id = "62342", 
        rank = 0, 
        sessionType = InteractiveSessionType.WEB, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(OpenSessionWithProvider(
        providerDomain = "provider.example.com", 
        providerId = "example", 
        session = OpenSession.Web(
            domainOverride = null, 
            jobId = "62342", 
            rank = 0, 
            redirectClientTo = "app-gateway.provider.example.com?token=aa2dd29a-fe83-4201-b28e-fe211f94ac9d", 
        ), 
    )), 
)
*/

/* The user should now proceed to the link provided in the response */

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

# In this example, the user will create a Job which uses an Application that exposes a web interface

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-web-application",
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
            "parameters": null,
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
#             "id": "62342"
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/interactiveSession" -d '{
    "items": [
        {
            "id": "62342",
            "rank": 0,
            "sessionType": "WEB"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "providerDomain": "provider.example.com",
#             "providerId": "example",
#             "session": {
#                 "type": "web",
#                 "jobId": "62342",
#                 "rank": 0,
#                 "redirectClientTo": "app-gateway.provider.example.com?token=aa2dd29a-fe83-4201-b28e-fe211f94ac9d",
#                 "domainOverride": null
#             }
#         }
#     ]
# }

# The user should now proceed to the link provided in the response

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_web.png)

</details>



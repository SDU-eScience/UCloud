[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Using a remote desktop Application (VNC)

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

/* In this example, the user will create a Job which uses an Application that exposes a VNC interface */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-remote-desktop", 
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
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "51231", 
    )), 
)
*/
Jobs.openInteractiveSession.call(
    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(
        id = "51231", 
        rank = 0, 
        sessionType = InteractiveSessionType.VNC, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(OpenSessionWithProvider(
        providerDomain = "provider.example.com", 
        providerId = "example", 
        session = OpenSession.Vnc(
            jobId = "51231", 
            password = "e7ccc6e0870250073286c44545e6b41820d1db7f", 
            rank = 0, 
            url = "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com", 
        ), 
    )), 
)
*/

/* The user can now connect to the remote desktop using the VNC protocol with the above details */


/* NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
established directly from the browser.

You can read more about the protocol here: https://novnc.com */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, the user will create a Job which uses an Application that exposes a VNC interface */

// Authenticated as user
await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-remote-desktop",
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
                "openedFile": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "51231"
        }
    ]
}
*/
await callAPI(JobsApi.openInteractiveSession(
    {
        "items": [
            {
                "id": "51231",
                "rank": 0,
                "sessionType": "VNC"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "providerDomain": "provider.example.com",
            "providerId": "example",
            "session": {
                "type": "vnc",
                "jobId": "51231",
                "rank": 0,
                "url": "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com",
                "password": "e7ccc6e0870250073286c44545e6b41820d1db7f"
            }
        }
    ]
}
*/

/* The user can now connect to the remote desktop using the VNC protocol with the above details */


/* NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
established directly from the browser.

You can read more about the protocol here: https://novnc.com */

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

# In this example, the user will create a Job which uses an Application that exposes a VNC interface

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-remote-desktop",
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
            "openedFile": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "51231"
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/interactiveSession" -d '{
    "items": [
        {
            "id": "51231",
            "rank": 0,
            "sessionType": "VNC"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "providerDomain": "provider.example.com",
#             "providerId": "example",
#             "session": {
#                 "type": "vnc",
#                 "jobId": "51231",
#                 "rank": 0,
#                 "url": "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com",
#                 "password": "e7ccc6e0870250073286c44545e6b41820d1db7f"
#             }
#         }
#     ]
# }

# The user can now connect to the remote desktop using the VNC protocol with the above details

# NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
# established directly from the browser.
# 
# You can read more about the protocol here: https://novnc.com

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_vnc.png)

</details>



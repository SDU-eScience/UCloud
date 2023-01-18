[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# Example: Ensuring UCloud/Core and Provider are in-sync

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>One or more active Jobs for this Provider</li>
</ul></td></tr>
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

/* In this example, we will explore the mechanism that UCloud/Core uses to ensure that the Provider
is synchronized with the core. */


/* UCloud/Core will periodically send the Provider a batch of active Jobs. If the Provider is unable
to recognize one or more of these Jobs, it should respond by updating the state of the affected
Job(s). */

JobsProvider.verify.call(
    bulkRequestOf(Job(
        createdAt = 1633329776235, 
        id = "54112", 
        output = null, 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = null, 
        specification = JobSpecification(
            allowDuplicateJob = false, 
            application = NameAndVersion(
                name = "acme-batch", 
                version = "1.0.0", 
            ), 
            name = null, 
            openedFile = null, 
            parameters = mapOf("debug" to AppParameterValue.Bool(
                value = true, 
            ), "value" to AppParameterValue.Text(
                value = "Hello, World!", 
            )), 
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute-1", 
                provider = "example", 
            ), 
            replicas = 1, 
            resources = null, 
            restartOnExit = null, 
            sshEnabled = null, 
            timeAllocation = SimpleDuration(
                hours = 1, 
                minutes = 0, 
                seconds = 0, 
            ), 
        ), 
        status = JobStatus(
            allowRestart = false, 
            expiresAt = null, 
            jobParametersJson = null, 
            resolvedApplication = null, 
            resolvedProduct = null, 
            resolvedSupport = null, 
            startedAt = null, 
            state = JobState.RUNNING, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "54112", 
    )),
    ucloud
).orThrow()

/*
Unit
*/

/* In this case, the Provider does not recognize 54112 */

JobsControl.update.call(
    bulkRequestOf(ResourceUpdateAndId(
        id = "54112", 
        update = JobUpdate(
            allowRestart = null, 
            expectedDifferentState = null, 
            expectedState = null, 
            newMounts = null, 
            newTimeAllocation = null, 
            outputFolder = null, 
            state = JobState.FAILURE, 
            status = "Your job is no longer available", 
            timestamp = 0, 
        ), 
    )),
    provider
).orThrow()

/*
Unit
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

# In this example, we will explore the mechanism that UCloud/Core uses to ensure that the Provider
# is synchronized with the core.

# UCloud/Core will periodically send the Provider a batch of active Jobs. If the Provider is unable
# to recognize one or more of these Jobs, it should respond by updating the state of the affected
# Job(s).

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/jobs/verify" -d '{
    "items": [
        {
            "id": "54112",
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "updates": [
            ],
            "specification": {
                "application": {
                    "name": "acme-batch",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute-1",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": null,
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": {
                    "debug": {
                        "type": "boolean",
                        "value": true
                    },
                    "value": {
                        "type": "text",
                        "value": "Hello, World!"
                    }
                },
                "resources": null,
                "timeAllocation": {
                    "hours": 1,
                    "minutes": 0,
                    "seconds": 0
                },
                "openedFile": null,
                "restartOnExit": null,
                "sshEnabled": null
            },
            "status": {
                "state": "RUNNING",
                "jobParametersJson": null,
                "startedAt": null,
                "expiresAt": null,
                "resolvedApplication": null,
                "resolvedSupport": null,
                "resolvedProduct": null,
                "allowRestart": false
            },
            "createdAt": 1633329776235,
            "output": null,
            "permissions": null
        }
    ]
}'


# {
# }

# In this case, the Provider does not recognize 54112

# Authenticated as provider
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/control/update" -d '{
    "items": [
        {
            "id": "54112",
            "update": {
                "state": "FAILURE",
                "outputFolder": null,
                "status": "Your job is no longer available",
                "expectedState": null,
                "expectedDifferentState": null,
                "newTimeAllocation": null,
                "allowRestart": null,
                "newMounts": null,
                "timestamp": 0
            }
        }
    ]
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs.provider.PROVIDERID_verify.png)

</details>



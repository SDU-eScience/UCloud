[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Tasks](/docs/developer-guide/core/communication/tasks.md)

# Example: Counting to 3 (Produced by the service)

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
Tasks.create.call(
    CreateRequest(
        initialStatus = null, 
        owner = "User#1234", 
        title = "We are counting to 3", 
    ),
    ucloud
).orThrow()

/*
Task(
    complete = false, 
    jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
    modifiedAt = 0, 
    owner = "User#1234", 
    processor = "_ucloud", 
    startedAt = 0, 
    status = null, 
    title = "We are counting to 3", 
)
*/
Tasks.postStatus.call(
    PostStatusRequest(
        update = TaskUpdate(
            complete = false, 
            jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
            messageToAppend = "Count is now 1", 
            newStatus = null, 
            newTitle = null, 
            progress = null, 
            speeds = emptyList(), 
        ), 
    ),
    ucloud
).orThrow()

/*
Unit
*/
Tasks.postStatus.call(
    PostStatusRequest(
        update = TaskUpdate(
            complete = false, 
            jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
            messageToAppend = "Count is now 2", 
            newStatus = null, 
            newTitle = null, 
            progress = null, 
            speeds = emptyList(), 
        ), 
    ),
    ucloud
).orThrow()

/*
Unit
*/
Tasks.postStatus.call(
    PostStatusRequest(
        update = TaskUpdate(
            complete = false, 
            jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
            messageToAppend = "Count is now 3", 
            newStatus = null, 
            newTitle = null, 
            progress = null, 
            speeds = emptyList(), 
        ), 
    ),
    ucloud
).orThrow()

/*
Unit
*/
Tasks.markAsComplete.call(
    FindByStringId(
        id = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
    ),
    ucloud
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

# Authenticated as ucloud
curl -XPUT -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/tasks" -d '{
    "title": "We are counting to 3",
    "owner": "User#1234",
    "initialStatus": null
}'


# {
#     "jobId": "b06f51d2-88af-487c-bb4c-4cc156cf24fd",
#     "owner": "User#1234",
#     "processor": "_ucloud",
#     "title": "We are counting to 3",
#     "status": null,
#     "complete": false,
#     "startedAt": 0,
#     "modifiedAt": 0
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/tasks/postStatus" -d '{
    "update": {
        "jobId": "b06f51d2-88af-487c-bb4c-4cc156cf24fd",
        "newTitle": null,
        "speeds": [
        ],
        "progress": null,
        "complete": false,
        "messageToAppend": "Count is now 1",
        "newStatus": null
    }
}'


# {
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/tasks/postStatus" -d '{
    "update": {
        "jobId": "b06f51d2-88af-487c-bb4c-4cc156cf24fd",
        "newTitle": null,
        "speeds": [
        ],
        "progress": null,
        "complete": false,
        "messageToAppend": "Count is now 2",
        "newStatus": null
    }
}'


# {
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/tasks/postStatus" -d '{
    "update": {
        "jobId": "b06f51d2-88af-487c-bb4c-4cc156cf24fd",
        "newTitle": null,
        "speeds": [
        ],
        "progress": null,
        "complete": false,
        "messageToAppend": "Count is now 3",
        "newStatus": null
    }
}'


# {
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/tasks/markAsComplete" -d '{
    "id": "b06f51d2-88af-487c-bb4c-4cc156cf24fd"
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/task_counting-task.png)

</details>



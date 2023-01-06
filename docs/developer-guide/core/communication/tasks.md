<p align='center'>
<a href='/docs/developer-guide/core/communication/notifications.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/communication/support.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / Tasks
# Tasks

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Tasks give services a way to communicate progress to end-users._

## Rationale

A task in UCloud displays the progress of any long-running process. Both services and providers use this functionality.
Each task is uniquely identified by a key. Each task belongs to a specific end-user. Services/providers communicate 
progress updates regularly. If the end-user is online when an update occurs, then the end-user is notified.

Providers use this functionality through one of the Control interfaces. They do not invoke the interface directly.

## Table of Contents
<details>
<summary>
<a href='#example-counting-to-3-(produced-by-the-service)'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-counting-to-3-(produced-by-the-service)'>Counting to 3 (Produced by the service)</a></td></tr>
<tr><td><a href='#example-counting-to-3-(received-by-end-user)'>Counting to 3 (Received by end-user)</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#list'><code>list</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listen'><code>listen</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#view'><code>view</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#markascomplete'><code>markAsComplete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#poststatus'><code>postStatus</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#progress'><code>Progress</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#speed'><code>Speed</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#task'><code>Task</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#taskupdate'><code>TaskUpdate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createrequest'><code>CreateRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listrequest'><code>ListRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#poststatusrequest'><code>PostStatusRequest</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Counting to 3 (Produced by the service)
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


## Example: Counting to 3 (Received by end-user)
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
Tasks.listen.subscribe(
    Unit,
    user,
    handler = { /* will receive messages listed below */ }
)

/*
TaskUpdate(
    complete = false, 
    jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
    messageToAppend = "Count is now 1", 
    newStatus = null, 
    newTitle = null, 
    progress = null, 
    speeds = emptyList(), 
)
*/

/*
TaskUpdate(
    complete = false, 
    jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
    messageToAppend = "Count is now 2", 
    newStatus = null, 
    newTitle = null, 
    progress = null, 
    speeds = emptyList(), 
)
*/

/*
TaskUpdate(
    complete = false, 
    jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
    messageToAppend = "Count is now 3", 
    newStatus = null, 
    newTitle = null, 
    progress = null, 
    speeds = emptyList(), 
)
*/

/*
TaskUpdate(
    complete = true, 
    jobId = "b06f51d2-88af-487c-bb4c-4cc156cf24fd", 
    messageToAppend = null, 
    newStatus = null, 
    newTitle = null, 
    progress = null, 
    speeds = emptyList(), 
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

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/task_counting-task-2.png)

</details>



## Remote Procedure Calls

### `list`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listrequest'>ListRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#task'>Task</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listen`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#taskupdate'>TaskUpdate</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `view`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#task'>Task</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#createrequest'>CreateRequest</a></code>|<code><a href='#task'>Task</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `markAsComplete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `postStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#poststatusrequest'>PostStatusRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Progress`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Progress(
    val title: String,
    val current: Int,
    val maximum: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>current</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>maximum</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `Speed`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Speed(
    val title: String,
    val speed: Double,
    val unit: String,
    val asText: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>speed</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code>
</summary>





</details>

<details>
<summary>
<code>unit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>asText</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `Task`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Task(
    val jobId: String,
    val owner: String,
    val processor: String,
    val title: String?,
    val status: String?,
    val complete: Boolean,
    val startedAt: Long,
    val modifiedAt: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>processor</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>complete</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>startedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `TaskUpdate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TaskUpdate(
    val jobId: String,
    val newTitle: String?,
    val speeds: List<Speed>?,
    val progress: Progress?,
    val complete: Boolean?,
    val messageToAppend: String?,
    val newStatus: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>speeds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#speed'>Speed</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>progress</code>: <code><code><a href='#progress'>Progress</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>complete</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>messageToAppend</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newStatus</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `CreateRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CreateRequest(
    val title: String,
    val owner: String,
    val initialStatus: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>initialStatus</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ListRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListRequest(
    val itemsPerPage: Int?,
    val page: Int?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `PostStatusRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PostStatusRequest(
    val update: TaskUpdate,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>update</code>: <code><code><a href='#taskupdate'>TaskUpdate</a></code></code>
</summary>





</details>



</details>



---


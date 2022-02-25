[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Tasks](/docs/developer-guide/core/communication/tasks.md)

# Example: Counting to 3 (Received by end-user)

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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
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



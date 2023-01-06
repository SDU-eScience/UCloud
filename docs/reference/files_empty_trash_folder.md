[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Emptying trash folder

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A trash folder located at /home/trash</li>
<li>The trash folder contains two files and a folder</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>The folder and all children are removed from the trash folder</li>
<li>The files is removed from the trash folder</li>
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
Files.trash.call(
    bulkRequestOf(FindByPath(
        id = "/home/trash", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete()), 
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

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/trash" -d '{
    "items": [
        {
            "id": "/home/trash"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_empty_trash_folder.png)

</details>



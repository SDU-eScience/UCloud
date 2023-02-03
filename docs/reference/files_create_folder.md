[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Creating a folder

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder</li>
<li>The user has EDIT permissions on the file</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A new file exists at '/123/folder/a</li>
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
Files.createFolder.call(
    bulkRequestOf(FilesCreateFolderRequestItem(
        conflictPolicy = WriteConflictPolicy.REJECT, 
        id = "/123/folder/a", 
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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/folder" -d '{
    "items": [
        {
            "id": "/123/folder/a",
            "conflictPolicy": "REJECT"
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

![](/docs/diagrams/files_create_folder.png)

</details>



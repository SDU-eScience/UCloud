[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Moving multiple files to trash

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder'</li>
<li>A file at '/123/file'</li>
<li>The user has EDIT permissions for all files involved</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>The folder and all children are moved to the provider's trash folder</li>
<li>The file is moved to the provider's trash folder</li>
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
        id = "/123/folder", 
    ), FindByPath(
        id = "/123/file", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(LongRunningTask.Complete(), LongRunningTask.Complete()), 
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
            "id": "/123/folder"
        },
        {
            "id": "/123/file"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "type": "complete"
#         },
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

![](/docs/diagrams/files_move_to_trash.png)

</details>



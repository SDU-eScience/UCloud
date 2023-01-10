[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Renaming a file

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User-initiated action, typically though the user-interface</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file present at /123/my/file</li>
<li>The user has EDIT permissions on the file</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>The file is moved to /123/my/new_file</li>
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
Files.move.call(
    bulkRequestOf(FilesMoveRequestItem(
        conflictPolicy = WriteConflictPolicy.REJECT, 
        newId = "/123/my/new_file", 
        oldId = "/123/my/file", 
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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/move" -d '{
    "items": [
        {
            "oldId": "/123/my/file",
            "newId": "/123/my/new_file",
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

![](/docs/diagrams/files_rename_file.png)

</details>



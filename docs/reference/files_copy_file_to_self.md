[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Copying a file to itself

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User-initiated action, typically through the user-interface</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file present at /123/my/file</li>
<li>The user has EDIT permissions on the file</li>
<li>The provider supports RENAME for conflict policies</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A new file present at '/123/my/file (1)'</li>
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
Files.copy.call(
    bulkRequestOf(FilesCopyRequestItem(
        conflictPolicy = WriteConflictPolicy.RENAME, 
        newId = "/123/my/file", 
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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/copy" -d '{
    "items": [
        {
            "oldId": "/123/my/file",
            "newId": "/123/my/file",
            "conflictPolicy": "RENAME"
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

![](/docs/diagrams/files_copy_file_to_self.png)

</details>



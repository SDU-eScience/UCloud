[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Uploading a file

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A folder at '/123/folder'</li>
<li>The user has EDIT permissions on the file</li>
<li>The provider supports the CHUNKED protocol</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A new file present at '/123/folder/file'</li>
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
Files.createUpload.call(
    bulkRequestOf(FilesCreateUploadRequestItem(
        conflictPolicy = WriteConflictPolicy.REJECT, 
        id = "/123/folder", 
        supportedProtocols = listOf(UploadProtocol.CHUNKED), 
        type = UploadType.FILE, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FilesCreateUploadResponseItem(
        endpoint = "https://provider.example.com/ucloud/example-provider/chunked", 
        protocol = UploadProtocol.CHUNKED, 
        token = "f1460d47e583653f7723204e5ff3f50bad91a658", 
    )), 
)
*/

/* The user can now proceed to upload using the chunked protocol at the provided endpoint */

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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/upload" -d '{
    "items": [
        {
            "id": "/123/folder",
            "type": "FILE",
            "supportedProtocols": [
                "CHUNKED"
            ],
            "conflictPolicy": "REJECT"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "endpoint": "https://provider.example.com/ucloud/example-provider/chunked",
#             "protocol": "CHUNKED",
#             "token": "f1460d47e583653f7723204e5ff3f50bad91a658"
#         }
#     ]
# }

# The user can now proceed to upload using the chunked protocol at the provided endpoint

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_upload.png)

</details>



[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# Example: Downloading a file

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A file at '/123/folder/file</li>
<li>The user has READ permissions on the file</li>
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
Files.createDownload.call(
    bulkRequestOf(FilesCreateDownloadRequestItem(
        id = "/123/folder/file", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FilesCreateDownloadResponseItem(
        endpoint = "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069", 
    )), 
)
*/

/* The user can now download the file through normal HTTP(s) GET at the provided endpoint */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(FilesApi.createDownload(
    {
        "items": [
            {
                "id": "/123/folder/file"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "endpoint": "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069"
        }
    ]
}
*/

/* The user can now download the file through normal HTTP(s) GET at the provided endpoint */

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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/download" -d '{
    "items": [
        {
            "id": "/123/folder/file"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "endpoint": "https://provider.example.com/ucloud/example-provider/download?token=d293435e94734c91394f17bb56268d3161c7f069"
#         }
#     ]
# }

# The user can now download the file through normal HTTP(s) GET at the provided endpoint

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files_download.png)

</details>



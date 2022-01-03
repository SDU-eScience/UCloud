[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Providers](/docs/developer-guide/accounting-and-projects/providers.md)

# Example: A Provider authenticating with UCloud/Core

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>The provider has already been registered with UCloud/Core</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* 📝 Note: The tokens shown here are not representative of tokens you will see in practice */

AuthProviders.refresh.call(
    bulkRequestOf(RefreshToken(
        refreshToken = "fb69e4367ee0fe4c76a4a926394aee547a41d998", 
    )),
    provider
).orThrow()

/*
BulkResponse(
    responses = listOf(AccessToken(
        accessToken = "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo", 
    )), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* 📝 Note: The tokens shown here are not representative of tokens you will see in practice */

// Authenticated as provider
await callAPI(AuthProvidersApi.refresh(
    {
        "items": [
            {
                "refreshToken": "fb69e4367ee0fe4c76a4a926394aee547a41d998"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "accessToken": "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
        }
    ]
}
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

# 📝 Note: The tokens shown here are not representative of tokens you will see in practice

# Authenticated as provider
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/auth/providers/refresh" -d '{
    "items": [
        {
            "refreshToken": "fb69e4367ee0fe4c76a4a926394aee547a41d998"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "accessToken": "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIjUF9leGFtcGxlIiwicm9sZSI6IlBST1ZJREVSIiwiaWF0IjoxNjMzNTIxMDA5LCJleHAiOjE2MzM1MjE5MTl9.P4zL-LBeahsga4eH0GqKpBmPf-Sa7pU70QhiXB1BchBe0DE9zuJ_6fws9cs9NOIo"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/providers_authentication.png)

</details>



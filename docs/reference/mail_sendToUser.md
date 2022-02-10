[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Mail](/docs/developer-guide/core/communication/mail.md)

# Example: Sending an email

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
MailDescriptions.sendToUser.call(
    bulkRequestOf(SendRequestItem(
        mail = Mail.LowFundsMail(
            categories = listOf("u1-standard"), 
            projectTitles = listOf("Science Project"), 
            providers = listOf("ucloud"), 
            subject = "Wallets low on resource", 
        ), 
        mandatory = false, 
        receiver = "User#1234", 
        receivingEmail = null, 
        testMail = null, 
    )),
    ucloud
).orThrow()

/*
Unit
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as ucloud
await callAPI(MailApi.sendToUser(
    {
        "items": [
            {
                "receiver": "User#1234",
                "mail": {
                    "type": "lowFunds",
                    "categories": [
                        "u1-standard"
                    ],
                    "providers": [
                        "ucloud"
                    ],
                    "projectTitles": [
                        "Science Project"
                    ],
                    "subject": "Wallets low on resource"
                },
                "mandatory": false,
                "receivingEmail": null,
                "testMail": null
            }
        ]
    }
);

/*
{
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

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/mail/sendToUser" -d '{
    "items": [
        {
            "receiver": "User#1234",
            "mail": {
                "type": "lowFunds",
                "categories": [
                    "u1-standard"
                ],
                "providers": [
                    "ucloud"
                ],
                "projectTitles": [
                    "Science Project"
                ],
                "subject": "Wallets low on resource"
            },
            "mandatory": false,
            "receivingEmail": null,
            "testMail": null
        }
    ]
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/mail_sendToUser.png)

</details>



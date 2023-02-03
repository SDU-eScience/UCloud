[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Mail](/docs/developer-guide/core/communication/mail.md)

# Example: Changing e-mail settings

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
MailDescriptions.retrieveEmailSettings.call(
    RetrieveEmailSettingsRequest(
        username = null, 
    ),
    user
).orThrow()

/*
RetrieveEmailSettingsResponse(
    settings = EmailSettings(
        applicationStatusChange = true, 
        applicationTransfer = true, 
        grantApplicationApproved = true, 
        grantApplicationRejected = true, 
        grantApplicationUpdated = true, 
        grantApplicationWithdrawn = true, 
        grantAutoApprove = true, 
        lowFunds = true, 
        newCommentOnApplication = true, 
        newGrantApplication = true, 
        projectUserInvite = true, 
        projectUserRemoved = true, 
        userLeft = true, 
        userRoleChange = true, 
        verificationReminder = true, 
    ), 
)
*/
MailDescriptions.toggleEmailSettings.call(
    bulkRequestOf(EmailSettingsItem(
        settings = EmailSettings(
            applicationStatusChange = true, 
            applicationTransfer = true, 
            grantApplicationApproved = true, 
            grantApplicationRejected = true, 
            grantApplicationUpdated = true, 
            grantApplicationWithdrawn = true, 
            grantAutoApprove = true, 
            lowFunds = true, 
            newCommentOnApplication = true, 
            newGrantApplication = true, 
            projectUserInvite = true, 
            projectUserRemoved = true, 
            userLeft = true, 
            userRoleChange = true, 
            verificationReminder = false, 
        ), 
        username = null, 
    )),
    user
).orThrow()

/*
Unit
*/
MailDescriptions.retrieveEmailSettings.call(
    RetrieveEmailSettingsRequest(
        username = null, 
    ),
    user
).orThrow()

/*
RetrieveEmailSettingsResponse(
    settings = EmailSettings(
        applicationStatusChange = true, 
        applicationTransfer = true, 
        grantApplicationApproved = true, 
        grantApplicationRejected = true, 
        grantApplicationUpdated = true, 
        grantApplicationWithdrawn = true, 
        grantAutoApprove = true, 
        lowFunds = true, 
        newCommentOnApplication = true, 
        newGrantApplication = true, 
        projectUserInvite = true, 
        projectUserRemoved = true, 
        userLeft = true, 
        userRoleChange = true, 
        verificationReminder = false, 
    ), 
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
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/mail/retrieveEmailSettings?" 

# {
#     "settings": {
#         "newGrantApplication": true,
#         "grantAutoApprove": true,
#         "grantApplicationUpdated": true,
#         "grantApplicationApproved": true,
#         "grantApplicationRejected": true,
#         "grantApplicationWithdrawn": true,
#         "newCommentOnApplication": true,
#         "applicationTransfer": true,
#         "applicationStatusChange": true,
#         "projectUserInvite": true,
#         "projectUserRemoved": true,
#         "verificationReminder": true,
#         "userRoleChange": true,
#         "userLeft": true,
#         "lowFunds": true
#     }
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/mail/toggleEmailSettings" -d '{
    "items": [
        {
            "username": null,
            "settings": {
                "newGrantApplication": true,
                "grantAutoApprove": true,
                "grantApplicationUpdated": true,
                "grantApplicationApproved": true,
                "grantApplicationRejected": true,
                "grantApplicationWithdrawn": true,
                "newCommentOnApplication": true,
                "applicationTransfer": true,
                "applicationStatusChange": true,
                "projectUserInvite": true,
                "projectUserRemoved": true,
                "verificationReminder": false,
                "userRoleChange": true,
                "userLeft": true,
                "lowFunds": true
            }
        }
    ]
}'


# {
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/mail/retrieveEmailSettings?" 

# {
#     "settings": {
#         "newGrantApplication": true,
#         "grantAutoApprove": true,
#         "grantApplicationUpdated": true,
#         "grantApplicationApproved": true,
#         "grantApplicationRejected": true,
#         "grantApplicationWithdrawn": true,
#         "newCommentOnApplication": true,
#         "applicationTransfer": true,
#         "applicationStatusChange": true,
#         "projectUserInvite": true,
#         "projectUserRemoved": true,
#         "verificationReminder": false,
#         "userRoleChange": true,
#         "userLeft": true,
#         "lowFunds": true
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/mail_emailSettings.png)

</details>



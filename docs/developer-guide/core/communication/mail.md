<p align='center'>
<a href='/docs/developer-guide/core/communication/slack.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/built-in-provider/storage.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / Mail
# Mail

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Internal service for sending e-mails._

## Rationale

Currently only one end-point is exposed for sending a single email to one user at a time, and only
`SERVICE` principals is authorized to do so.

Email templates are pre-defined and are not controllable by clients.

## Table of Contents
<details>
<summary>
<a href='#example-sending-an-email'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-sending-an-email'>Sending an email</a></td></tr>
<tr><td><a href='#example-forwarding-a-support-ticket-to-jira'>Forwarding a support ticket to Jira</a></td></tr>
<tr><td><a href='#example-changing-e-mail-settings'>Changing e-mail settings</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#retrieveemailsettings'><code>retrieveEmailSettings</code></a></td>
<td>Changes an end-user's e-mail preferences</td>
</tr>
<tr>
<td><a href='#sendsupport'><code>sendSupport</code></a></td>
<td>Forwards a support ticket into Jira</td>
</tr>
<tr>
<td><a href='#sendtouser'><code>sendToUser</code></a></td>
<td>Sends an email to an end-user based on a pre-defined template</td>
</tr>
<tr>
<td><a href='#toggleemailsettings'><code>toggleEmailSettings</code></a></td>
<td>Retrieves an end-user's e-mail preferences</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#emailsettings'><code>EmailSettings</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#emailsettingsitem'><code>EmailSettingsItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail'><code>Mail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantappautoapprovetoadminsmail'><code>Mail.GrantAppAutoApproveToAdminsMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationapprovemail'><code>Mail.GrantApplicationApproveMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationapprovemailtoadmins'><code>Mail.GrantApplicationApproveMailToAdmins</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationrejectedmail'><code>Mail.GrantApplicationRejectedMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationstatuschangedtoadmin'><code>Mail.GrantApplicationStatusChangedToAdmin</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationupdatedmail'><code>Mail.GrantApplicationUpdatedMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationupdatedmailtoadmins'><code>Mail.GrantApplicationUpdatedMailToAdmins</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.grantapplicationwithdrawnmail'><code>Mail.GrantApplicationWithdrawnMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.lowfundsmail'><code>Mail.LowFundsMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.newcommentonapplicationmail'><code>Mail.NewCommentOnApplicationMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.newgrantapplicationmail'><code>Mail.NewGrantApplicationMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.projectinvitemail'><code>Mail.ProjectInviteMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.resetpasswordmail'><code>Mail.ResetPasswordMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.stilllowfundsmail'><code>Mail.StillLowFundsMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.transferapplicationmail'><code>Mail.TransferApplicationMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.userleftmail'><code>Mail.UserLeftMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.userremovedmail'><code>Mail.UserRemovedMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.userremovedmailtouser'><code>Mail.UserRemovedMailToUser</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.userrolechangemail'><code>Mail.UserRoleChangeMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mail.verificationremindermail'><code>Mail.VerificationReminderMail</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveemailsettingsrequest'><code>RetrieveEmailSettingsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#sendrequestitem'><code>SendRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#sendsupportemailrequest'><code>SendSupportEmailRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveemailsettingsresponse'><code>RetrieveEmailSettingsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Sending an email
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


## Example: Forwarding a support ticket to Jira
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
MailDescriptions.sendSupport.call(
    SendSupportEmailRequest(
        fromEmail = "foo@bar", 
        message = "Message", 
        subject = "Subject", 
    ),
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
await callAPI(MailApi.sendSupport(
    {
        "fromEmail": "foo@bar",
        "subject": "Subject",
        "message": "Message"
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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/mail/support" -d '{
    "fromEmail": "foo@bar",
    "subject": "Subject",
    "message": "Message"
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/mail_support.png)

</details>


## Example: Changing e-mail settings
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(MailApi.retrieveEmailSettings(
    {
        "username": null
    }
);

/*
{
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
        "verificationReminder": true,
        "userRoleChange": true,
        "userLeft": true,
        "lowFunds": true
    }
}
*/
await callAPI(MailApi.toggleEmailSettings(
    {
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
    }
);

/*
{
}
*/
await callAPI(MailApi.retrieveEmailSettings(
    {
        "username": null
    }
);

/*
{
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



## Remote Procedure Calls

### `retrieveEmailSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Changes an end-user's e-mail preferences_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#retrieveemailsettingsrequest'>RetrieveEmailSettingsRequest</a></code>|<code><a href='#retrieveemailsettingsresponse'>RetrieveEmailSettingsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `sendSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Forwards a support ticket into Jira_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#sendsupportemailrequest'>SendSupportEmailRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

NOTE: This endpoint is meant only for use by SDU and might not work well with other deployments.


### `sendToUser`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Sends an email to an end-user based on a pre-defined template_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#sendrequestitem'>SendRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `toggleEmailSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves an end-user's e-mail preferences_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#emailsettingsitem'>EmailSettingsItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `EmailSettings`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class EmailSettings(
    val newGrantApplication: Boolean?,
    val grantAutoApprove: Boolean?,
    val grantApplicationUpdated: Boolean?,
    val grantApplicationApproved: Boolean?,
    val grantApplicationRejected: Boolean?,
    val grantApplicationWithdrawn: Boolean?,
    val newCommentOnApplication: Boolean?,
    val applicationTransfer: Boolean?,
    val applicationStatusChange: Boolean?,
    val projectUserInvite: Boolean?,
    val projectUserRemoved: Boolean?,
    val verificationReminder: Boolean?,
    val userRoleChange: Boolean?,
    val userLeft: Boolean?,
    val lowFunds: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>newGrantApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>grantAutoApprove</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>grantApplicationUpdated</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>grantApplicationApproved</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>grantApplicationRejected</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>grantApplicationWithdrawn</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newCommentOnApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>applicationTransfer</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>applicationStatusChange</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>projectUserInvite</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>projectUserRemoved</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>verificationReminder</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>userRoleChange</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>userLeft</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>lowFunds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `EmailSettingsItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class EmailSettingsItem(
    val username: String?,
    val settings: EmailSettings,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>settings</code>: <code><code><a href='#emailsettings'>EmailSettings</a></code></code>
</summary>





</details>



</details>



---

### `Mail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Mail {
    abstract val subject: String

    class GrantAppAutoApproveToAdminsMail : Mail()
    class GrantApplicationApproveMail : Mail()
    class GrantApplicationApproveMailToAdmins : Mail()
    class GrantApplicationRejectedMail : Mail()
    class GrantApplicationStatusChangedToAdmin : Mail()
    class GrantApplicationUpdatedMail : Mail()
    class GrantApplicationUpdatedMailToAdmins : Mail()
    class GrantApplicationWithdrawnMail : Mail()
    class LowFundsMail : Mail()
    class NewCommentOnApplicationMail : Mail()
    class NewGrantApplicationMail : Mail()
    class ProjectInviteMail : Mail()
    class ResetPasswordMail : Mail()
    class StillLowFundsMail : Mail()
    class TransferApplicationMail : Mail()
    class UserLeftMail : Mail()
    class UserRemovedMail : Mail()
    class UserRemovedMailToUser : Mail()
    class UserRoleChangeMail : Mail()
    class VerificationReminderMail : Mail()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `Mail.GrantAppAutoApproveToAdminsMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantAppAutoApproveToAdminsMail(
    val sender: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "autoApproveGrant" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "autoApproveGrant" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationApproveMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationApproveMail(
    val projectTitle: String,
    val subject: String?,
    val type: String /* "applicationApproved" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationApproved" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationApproveMailToAdmins`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationApproveMailToAdmins(
    val sender: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "applicationApprovedToAdmins" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationApprovedToAdmins" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationRejectedMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationRejectedMail(
    val projectTitle: String,
    val subject: String?,
    val type: String /* "applicationRejected" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationRejected" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationStatusChangedToAdmin`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationStatusChangedToAdmin(
    val status: String,
    val projectTitle: String,
    val sender: String,
    val receivingProjectTitle: String,
    val subject: String?,
    val type: String /* "applicationStatusChangedToAdmins" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>receivingProjectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationStatusChangedToAdmins" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationUpdatedMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationUpdatedMail(
    val projectTitle: String,
    val sender: String,
    val subject: String?,
    val type: String /* "applicationUpdated" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationUpdated" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationUpdatedMailToAdmins`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationUpdatedMailToAdmins(
    val projectTitle: String,
    val sender: String,
    val receivingProjectTitle: String,
    val subject: String?,
    val type: String /* "applicationUpdatedToAdmins" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>receivingProjectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationUpdatedToAdmins" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.GrantApplicationWithdrawnMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class GrantApplicationWithdrawnMail(
    val projectTitle: String,
    val sender: String,
    val subject: String?,
    val type: String /* "applicationWithdrawn" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "applicationWithdrawn" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.LowFundsMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LowFundsMail(
    val categories: List<String>,
    val providers: List<String>,
    val projectTitles: List<String>,
    val subject: String?,
    val type: String /* "lowFunds" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>categories</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>providers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitles</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "lowFunds" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.NewCommentOnApplicationMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewCommentOnApplicationMail(
    val sender: String,
    val projectTitle: String,
    val receivingProjectTitle: String,
    val subject: String?,
    val type: String /* "newComment" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>receivingProjectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "newComment" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.NewGrantApplicationMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NewGrantApplicationMail(
    val sender: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "newGrantApplication" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sender</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "newGrantApplication" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.ProjectInviteMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProjectInviteMail(
    val projectTitle: String,
    val subject: String?,
    val type: String /* "invitedToProject" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "invitedToProject" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.ResetPasswordMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResetPasswordMail(
    val token: String,
    val subject: String?,
    val type: String /* "resetPassword" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>token</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "resetPassword" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.StillLowFundsMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class StillLowFundsMail(
    val category: String,
    val provider: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "stillLowFunds" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>provider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "stillLowFunds" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.TransferApplicationMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TransferApplicationMail(
    val senderProject: String,
    val receiverProject: String,
    val applicationProjectTitle: String,
    val subject: String?,
    val type: String /* "transferApplication" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>senderProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>receiverProject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>applicationProjectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "transferApplication" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.UserLeftMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserLeftMail(
    val leavingUser: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "userLeft" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>leavingUser</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "userLeft" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.UserRemovedMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserRemovedMail(
    val leavingUser: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "userRemoved" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>leavingUser</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "userRemoved" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.UserRemovedMailToUser`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserRemovedMailToUser(
    val projectTitle: String,
    val subject: String?,
    val type: String /* "userRemovedToUser" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "userRemovedToUser" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.UserRoleChangeMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserRoleChangeMail(
    val subjectToChange: String,
    val roleChange: String,
    val projectTitle: String,
    val subject: String?,
    val type: String /* "userRoleChange" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>subjectToChange</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>roleChange</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "userRoleChange" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Mail.VerificationReminderMail`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class VerificationReminderMail(
    val projectTitle: String,
    val role: String,
    val subject: String?,
    val type: String /* "verificationReminder" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>role</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "verificationReminder" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `RetrieveEmailSettingsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RetrieveEmailSettingsRequest(
    val username: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `SendRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SendRequestItem(
    val receiver: String,
    val mail: Mail,
    val mandatory: Boolean?,
    val receivingEmail: String?,
    val testMail: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>receiver</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>mail</code>: <code><code><a href='#mail'>Mail</a></code></code>
</summary>





</details>

<details>
<summary>
<code>mandatory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>receivingEmail</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>testMail</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `SendSupportEmailRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SendSupportEmailRequest(
    val fromEmail: String,
    val subject: String,
    val message: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>fromEmail</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>message</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `RetrieveEmailSettingsResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RetrieveEmailSettingsResponse(
    val settings: EmailSettings,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>settings</code>: <code><code><a href='#emailsettings'>EmailSettings</a></code></code>
</summary>





</details>



</details>



---


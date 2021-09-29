# Mail

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



## Remote Procedure Calls

### `sendSupport`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#sendsupportemailrequest'>SendSupportEmailRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `send`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#sendrequest'>SendRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `sendBulk`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#sendbulkrequest'>SendBulkRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `toggleEmailSettings`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#emailsettingsitem'>EmailSettingsItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveEmailSettings`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#retrieveemailsettingsrequest'>RetrieveEmailSettingsRequest</a></code>|<code><a href='#retrieveemailsettingsresponse'>RetrieveEmailSettingsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `EmailSettings`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
sealed class Mail {
    abstract val subject: String

    class TransferApplicationMail : Mail()
    class LowFundsMail : Mail()
    class StillLowFundsMail : Mail()
    class UserRoleChangeMail : Mail()
    class UserLeftMail : Mail()
    class UserRemovedMail : Mail()
    class UserRemovedMailToUser : Mail()
    class ProjectInviteMail : Mail()
    class NewGrantApplicationMail : Mail()
    class GrantAppAutoApproveToAdminsMail : Mail()
    class GrantApplicationUpdatedMail : Mail()
    class GrantApplicationUpdatedMailToAdmins : Mail()
    class GrantApplicationStatusChangedToAdmin : Mail()
    class GrantApplicationApproveMail : Mail()
    class GrantApplicationApproveMailToAdmins : Mail()
    class GrantApplicationRejectedMail : Mail()
    class GrantApplicationWithdrawnMail : Mail()
    class NewCommentOnApplicationMail : Mail()
    class ResetPasswordMail : Mail()
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

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationApproveMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationApproveMailToAdmins`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationRejectedMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationStatusChangedToAdmin`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationUpdatedMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationUpdatedMailToAdmins`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.GrantApplicationWithdrawnMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.LowFundsMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.NewCommentOnApplicationMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.NewGrantApplicationMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.ProjectInviteMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.ResetPasswordMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.StillLowFundsMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.TransferApplicationMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.UserLeftMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.UserRemovedMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.UserRemovedMailToUser`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.UserRoleChangeMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `Mail.VerificationReminderMail`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)




</details>



</details>



---

### `RetrieveEmailSettingsRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

### `SendBulkRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class SendBulkRequest(
    val messages: List<SendRequest>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>messages</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#sendrequest'>SendRequest</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `SendRequest`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class SendRequest(
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

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



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


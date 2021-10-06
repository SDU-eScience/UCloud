[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Mail](/docs/developer-guide/core/communication/mail.md)

# `Mail`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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



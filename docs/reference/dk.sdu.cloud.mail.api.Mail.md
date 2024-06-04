[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Mail](/docs/developer-guide/core/communication/mail.md)

# `Mail`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Mail {
    abstract val subject: String

    class GrantApplicationApproveMail : Mail()
    class GrantApplicationApproveMailToAdmins : Mail()
    class GrantApplicationRejectedMail : Mail()
    class GrantApplicationStatusChangedToAdmin : Mail()
    class GrantApplicationUpdatedMail : Mail()
    class GrantApplicationUpdatedMailToAdmins : Mail()
    class GrantApplicationWithdrawnMail : Mail()
    class JobEvents : Mail()
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
    class VerifyEmailAddress : Mail()
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



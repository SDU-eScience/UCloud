package dk.sdu.cloud.mail.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailSettings(
    //Grant applications
    val newGrantApplication: Boolean = true,
    val grantApplicationUpdated: Boolean = true,
    val grantApplicationApproved: Boolean = true,
    val grantApplicationRejected: Boolean = true,
    val grantApplicationWithdrawn: Boolean = true,
    val newCommentOnApplication: Boolean = true,
    val applicationTransfer: Boolean = true,
    val applicationStatusChange: Boolean = true,
    //Project
    val projectUserInvite: Boolean = true,
    val projectUserRemoved: Boolean = true,
    val verificationReminder: Boolean = true,
    val userRoleChange: Boolean = true,
    val userLeft: Boolean = true,
    val lowFunds: Boolean = true,
)


@Serializable
sealed class Mail {
    abstract val subject: String
    @Serializable
    @SerialName("transferApplication")
    data class TransferApplicationMail(
        val senderProject: String,
        val receiverProject: String,
        val applicationProjectTitle: String,
        override val subject: String = "Application transfer"
    ): Mail()

    @Serializable
    @SerialName("lowFunds")
    data class LowFundsMail(
        val categories: List<String>,
        val providers: List<String>,
        val projectTitles: List<String?>,
        override val subject: String = "Wallets low on resource"
    ): Mail() {
        init {
            require(categories.size == providers.size)
            require(projectTitles.size == providers.size)
        }
    }

    @Serializable
    @SerialName("stillLowFunds")
    data class StillLowFundsMail(
        val category: String,
        val provider: String,
        val projectTitle: String,
        override val subject: String = "Project low on resource"
    ): Mail()


    @Serializable
    @SerialName("userRoleChange")
    data class UserRoleChangeMail(
        val subjectToChange: String,
        val roleChange: String,
        val projectTitle: String,
        override val subject: String = "Role change in project"
    ): Mail()

    @Serializable
    @SerialName("userLeft")
    data class UserLeftMail(
        val leavingUser: String,
        val projectTitle: String,
        override val subject: String = "User left project"
    ): Mail()

    @Serializable
    @SerialName("userRemoved")
    data class UserRemovedMail(
        val leavingUser: String,
        val projectTitle: String,
        override val subject: String = "User removed from project"
    ): Mail()

    @Serializable
    @SerialName("userRemovedToUser")
    data class UserRemovedMailToUser(
        val projectTitle: String,
        override val subject: String = "No longer part of project"
    ):Mail()

    @Serializable
    @SerialName("invitedToProject")
    data class ProjectInviteMail(
        val projectTitle: String,
        override val subject: String = "Invite to project"
    ): Mail()

    @Serializable
    @SerialName("newGrantApplication")
    data class NewGrantApplicationMail(
        val sender: String,
        val projectTitle: String,
        override val subject: String = "New grant application"
    ): Mail()

    @Serializable
    @SerialName("applicationUpdated")
    data class GrantApplicationUpdatedMail(
        val projectTitle: String,
        val sender: String,
        override val subject: String = "Grant application updated"
    ): Mail()

    @Serializable
    @SerialName("applicationUpdatedToAdmins")
    data class GrantApplicationUpdatedMailToAdmins(
        val projectTitle: String,
        val sender: String,
        val receivingProjectTitle: String,
        override val subject: String = "Grant application updated"
    ): Mail()

    @Serializable
    @SerialName("applicationStatusChangedToAdmins")
    data class GrantApplicationStatusChangedToAdmin(
        val status: String,
        val projectTitle: String,
        val sender: String,
        val receivingProjectTitle: String,
        override val subject: String = "Application changed status"
    ): Mail()

    @Serializable
    @SerialName("applicationApproved")
    data class GrantApplicationApproveMail(
        val projectTitle: String,
        override val subject: String = "Grant application updated (Approved)"
    ): Mail()

    @Serializable
    @SerialName("applicationApprovedToAdmins")
    data class GrantApplicationApproveMailToAdmins(
        val sender: String,
        val projectTitle: String,
        override val subject: String = "Grant application updated (Approved)"
    ): Mail()

    @Serializable
    @SerialName("applicationRejected")
    data class GrantApplicationRejectedMail(
        val projectTitle: String,
        override val subject: String = "Grant application updated (Rejected)"
    ): Mail()

    @Serializable
    @SerialName("applicationWithdrawn")
    data class GrantApplicationWithdrawnMail(
        val projectTitle: String,
        val sender: String,
        override val subject: String = "Grant application updated (Closed)"
    ): Mail()

    @Serializable
    @SerialName("newComment")
    data class NewCommentOnApplicationMail(
        val sender: String,
        val projectTitle: String,
        val receivingProjectTitle: String,
        override val subject: String = "New comment on Application"
    ): Mail()

    @Serializable
    @SerialName("resetPassword")
    data class ResetPasswordMail(
        val token: String,
        override val subject: String = "[UCloud] Reset of Password"
    ): Mail()

    @Serializable
    @SerialName("verificationReminder")
    data class VerificationReminderMail(
        val projectTitle: String,
        val role: String,
        override val subject: String = "Time to review your project"
    ): Mail()

    @Serializable
    @SerialName("verifyEmailAddress")
    data class VerifyEmailAddress(
        val type: String,
        val token: String,
        override val subject: String = "[UCloud] Please verify your email address",
        val username: String? = null,
    ) : Mail()
}

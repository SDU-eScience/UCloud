package dk.sdu.cloud.grant.utils

import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.service.escapeHtml

//TODO()
fun newCommentTemplate(receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>You have received a new comment from ${escapeHtml(sender)} in ${escapeHtml(projectTitle)}.</p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun newIngoingApplicationTemplate(receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>You have received a new application from ${escapeHtml(sender)} in ${escapeHtml(projectTitle)}.</p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun autoApproveTemplate(receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            You have received a new application from ${escapeHtml(sender)} in ${escapeHtml(projectTitle)} 
            which has been automatically approved by your project policy. 
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun responseTemplate(status: ApplicationStatus, receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        ${
            when {
                status == ApplicationStatus.APPROVED -> approved(projectTitle)
                status == ApplicationStatus.REJECTED -> rejected(projectTitle)
                status == ApplicationStatus.CLOSED -> closed(projectTitle, sender)
                else -> throw IllegalStateException()
            }
        }
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

private fun approved(projectTitle: String) =
    """
        <p>
            We are happy to tell you that your application for resources from 
            ${escapeHtml(projectTitle)} has been approved
        </p>
    """.trimIndent()

private fun rejected(projectTitle: String) =
    """
        <p>We are sorry, but your application for resources from ${escapeHtml(projectTitle)} has been rejected</p>
    """.trimIndent()

private fun closed(projectTitle: String, sender: String) =
    """
        <p>An application for ${escapeHtml(projectTitle)} has been closed by ${escapeHtml(sender)}</p>
    """.trimIndent()

fun updatedTemplate(projectTitle: String, receiver: String, sender: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            We write to inform you that your applications for resources in project ${escapeHtml(projectTitle)} 
            have been changed by ${escapeHtml(sender)}. You might want to check the changes to see if the 
            application still fit your needs.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

const val NO_NOTIFICATIONS_DISCLAIMER = "<p>If you do not want to receive these notifications per mail, " +
        "you can unsubscribe to non-crucial emails in your personal settings on UCloud</p>"

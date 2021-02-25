package dk.sdu.cloud.grant.utils

import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.service.escapeHtml

//TODO()
fun newCommentTemplate(receiver: String, sender: String, projectTitle: String, receivingProjectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            You have received a new comment from ${escapeHtml(sender)} in an application to the project 
            '${escapeHtml(projectTitle)}' regarding the project: '${escapeHtml(receivingProjectTitle)}'.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun newIngoingApplicationTemplate(receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            You have received a new application from ${escapeHtml(sender)} in the project 
            '${escapeHtml(projectTitle)}'.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun approvedProjectToAdminsTemplate(receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
        The application from ${escapeHtml(sender)} in the project 
        '${escapeHtml(projectTitle)}', have been approved. 
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun autoApproveTemplate(receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            You have received a new application from ${escapeHtml(sender)} in the project 
            '${escapeHtml(projectTitle)}', which has been automatically approved by your project policy. 
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

fun statusChangeTemplateToAdmins(status: ApplicationStatus, receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>The status of the application from ${escapeHtml(sender)} in the project '${escapeHtml(projectTitle)}' 
        has been changed to ${escapeHtml(status.name)}.
        </p>
    }
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

private fun approved(projectTitle: String) =
    """
        <p>
            We are happy to inform you that your application for resources from 
            '${escapeHtml(projectTitle)}' has been approved.
        </p>
    """.trimIndent()

private fun rejected(projectTitle: String) =
    """
        <p>
            We regret to inform you that your application for resources from '${escapeHtml(projectTitle)}' 
            has been rejected.
        </p>
    """.trimIndent()

private fun closed(projectTitle: String, sender: String) =
    """
        <p>An application for '${escapeHtml(projectTitle)}' has been withdrawn by ${escapeHtml(sender)}.</p>
    """.trimIndent()

fun updatedTemplate(projectTitle: String, receiver: String, sender: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            Your application for resources in project '${escapeHtml(projectTitle)}' has been changed by 
            ${escapeHtml(sender)}. Please review the changes to the application.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun updatedTemplateToAdmins(projectTitle: String, receiver: String, sender: String, receivingProjectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            The application for resources regarding project '${escapeHtml(receivingProjectTitle)}' in project 
            '${escapeHtml(projectTitle)}' has been changed by ${escapeHtml(sender)}.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

const val NO_NOTIFICATIONS_DISCLAIMER = """<p>If you do not want to receive these email notifications, 
    you can unsubscribe to non-critical emails in your personal settings on UCloud</p>"""

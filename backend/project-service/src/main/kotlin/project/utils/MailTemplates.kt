package dk.sdu.cloud.project.utils

import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.escapeHtml

fun userRoleChangeTemplate(
    recipient: String,
    subjectToChange: String,
    roleChange: ProjectRole,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>    
    <p>
        The role of the user ${escapeHtml(subjectToChange)} was changed to 
        ${roleChange.name} in the project ${escapeHtml(projectTitle)}.
    </p>
    $NO_NOTIFICATIONS_DISCLAIMER
    
""".trimIndent()

fun userLeftTemplate(
    recipient: String,
    leavingUser: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>
    <p>
        The user ${escapeHtml(leavingUser)} has left the project ${escapeHtml(projectTitle)}.
    </p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()

fun userRemovedTemplate(
    recipient: String,
    leavingUser: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>    
    <p>
        The user ${escapeHtml(leavingUser)} has been removed the project ${escapeHtml(projectTitle)}.
    </p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()

fun userRemovedToPersonRemovedTemplate(
    recipient: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>
    <p>You have been removed from the project ${escapeHtml(projectTitle)}.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()

fun userInvitedToInviteeTemplate(
    recipient: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>    
    <p>
        You have been invited to join the project ${escapeHtml(projectTitle)} on UCloud.
    </p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()


const val NO_NOTIFICATIONS_DISCLAIMER = """<p>If you do not want to receive these email notifications, 
    you can unsubscribe to non-critical emails in your personal settings on UCloud</p>"""

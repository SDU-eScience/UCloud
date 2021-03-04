package dk.sdu.cloud.mail.api

import dk.sdu.cloud.service.escapeHtml

//Remember to update DB column 'email_settings' in principals
enum class MailSubjects(val subject: String) {
    LOW_FUNDS_SUBJECT("Project low on resource"),
    USER_ROLE_CHANGE("Role change in project"),
    USER_LEFT("User left project"),
    PROJECT_USER_INVITE("User invited to project"),
    NEW_GRANT_APPLICATION("New grant application"),
    GRANT_APP_AUTO_APPROVE("Grant application for subproject automatically approved"),
    GRANT_APPLICATION_UPDATED("Grant application updated"),
    GRANT_APP_APPROVED("Grant application updated (Approved)"),
    GRANT_APP_REJECTED("Grant application updated (Rejected)"),
    GRANT_APP_WITHDRAWN("Grant application updated (Closed)"),
    COMMENT_GRANT_APPLICATION("Comment on Application"),
    RESET_PASSWORD("[UCloud] Reset of Password"), // ALWAYS true in DB
    VERIFICATION_REMINDER("Time to review your project");
}

const val NO_NOTIFICATIONS_DISCLAIMER = """<p>If you do not want to receive these email notifications, 
    you can unsubscribe to non-critical emails in your personal settings on UCloud</p>"""

fun verifyReminderTemplate(receiver: String, projectTitle: String, role: String) =
    """
        <p>Hello ${receiver},</p> 
        
        <p>
            It is time for a review of your project $projectTitle in which you are 
            ${if (role == "ADMIN") " an admin" else " a PI"}.
        </p>
        
        <ul>
            <li>PIs and admins are asked to occasionally review members of their project</li>
            <li>We ask you to ensure that only the people who need access have access</li>
            <li>
                If you find someone who should not have access then remove them by clicking 'Remove'
                next to their name
            </li>
            <li>
                You can begin the review by clicking 
                <a href="https://cloud.sdu.dk/app/projects}">here</a>.
            </li>
        </ul>

    """.trimMargin()

fun resetPasswordTemplate(receiver: String, token: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>We have received a request to reset your UCloud account password. To proceed, follow the link below.</p>
        <p>
            <a href="https://cloud.sdu.dk/app/login?password-reset=true&token=${token}">https://cloud.sdu.dk/app/login?password-reset=true&token=${token}</a>
        </p>
        <p>If you did not initiate this request, feel free to disregard this email, or reply to this email for support.</p>
    """.trimIndent()

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

fun statusChangeTemplateToAdmins(status: String, receiver: String, sender: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>The status of the application from ${escapeHtml(sender)} in the project '${escapeHtml(projectTitle)}' 
        has been changed to ${escapeHtml(status)}.
        </p>
    }
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

 fun approved(receiver: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            We are happy to inform you that your application for resources from 
            '${escapeHtml(projectTitle)}' has been approved.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

 fun rejected(receiver: String, projectTitle: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>
            We regret to inform you that your application for resources from '${escapeHtml(projectTitle)}' 
            has been rejected.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

 fun closed(receiver: String, projectTitle: String, sender: String) =
    """
        <p>Dear ${escapeHtml(receiver)}</p>
        <p>An application for '${escapeHtml(projectTitle)}' has been withdrawn by ${escapeHtml(sender)}.</p>
        $NO_NOTIFICATIONS_DISCLAIMER
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

fun userRoleChangeTemplate(
    recipient: String,
    subjectToChange: String,
    roleChange: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>    
        <p>
            The role of the user ${escapeHtml(subjectToChange)} was changed to 
            ${roleChange} in the project ${escapeHtml(projectTitle)}.
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

fun stillLowResources (
    recipient: String,
    catagory: String,
    provider: String,
    projectTitle: String
) = """
        <p>Dear ${escapeHtml(recipient)}</p>
        <p>
            The project ${escapeHtml(projectTitle)} is still low on the ${escapeHtml(catagory)} resource 
            from ${escapeHtml(provider)} after new resources were allocated. <br>
            If this was intentional, please ignore this message.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun lowResourcesTemplate(
    recipient: String,
    catagory: String,
    provider: String,
    projectTitle: String
) = """
        <p>Dear ${escapeHtml(recipient)}</p>
        <p>
            The project ${escapeHtml(projectTitle)} is running low on the ${escapeHtml(catagory)} resource 
            from ${escapeHtml(provider)}. <br>
            If needed, you can request additional resources from the project's resource page.
        </p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()



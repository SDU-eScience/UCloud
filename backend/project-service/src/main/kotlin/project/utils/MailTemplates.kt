package dk.sdu.cloud.project.utils

import dk.sdu.cloud.project.api.ProjectRole

fun userRoleChangeTemplate(
    recipient: String,
    subjectToChange:String,
    roleChange: ProjectRole,
    projectTitle: String
) = """
    <p>Dear $recipient</p>    
    <p>We write to you to inform you that $subjectToChange, has had their role changed to: ${roleChange.name}
    in the project: $projectTitle.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
    
""".trimIndent()

fun userLeftTemplate(
    recipient: String,
    leavingUser: String,
    projectTitle: String
) = """
    <p>Dear $recipient</p>
    <p>We write to you to inform you that $leavingUser has left the project: $projectTitle.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()

fun userRemovedTemplate(
    recipient: String,
    leavingUser: String,
    projectTitle: String
) = """
    <p>Dear $recipient</p>    
    <p>We write to you to inform you that $leavingUser has been removed the project: $projectTitle.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()

fun userRemovedToPersonRemovedTemplate(
    recipient: String,
    projectTitle: String
) = """
    <p>Dear $recipient</p>
    <p>We write to you to inform you that you have been removed the project: $projectTitle.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()


fun userInvitedTemplate(
    recipient: String,
    invitedUser: String,
    projectTitle: String
) = """
    <p>Dear $recipient</p>
    <p>We write to you to inform you that $invitedUser has been invited to the project: $projectTitle on UCloud.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()

fun userInvitedToInviteeTemplate(
    recipient: String,
    projectTitle: String
) = """
    <p>Dear $recipient</p>    
    <p>We write to you to inform you that you have been invited to the project: $projectTitle on UCloud.</p>
    $NO_NOTIFICATIONS_DISCLAIMER
""".trimIndent()


const val NO_NOTIFICATIONS_DISCLAIMER = "<p>If you do not want to receive these notifications per mail, " +
        "you can unsubscribe to none-crucial emails in your personal settings on UCloud</p>"

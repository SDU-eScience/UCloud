package dk.sdu.cloud.project.utils

import dk.sdu.cloud.project.api.ProjectRole

fun userRoleChangeTemplate(
    recipient: String,
    subjectToChange:String,
    roleChange: ProjectRole,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that $subjectToChange, has had their role changed to: ${roleChange.name}
    in the project: $projectTitle.
    
    $NO_NOTIFICATIONS_DISCLAIMER
    
"""

fun userLeftTemplate(
    recipient: String,
    leavingUser: String,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that $leavingUser has left the project: $projectTitle.
    
    $NO_NOTIFICATIONS_DISCLAIMER
"""
fun userRemovedTemplate(
    recipient: String,
    leavingUser: String,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that $leavingUser has been removed the project: $projectTitle.
    
    $NO_NOTIFICATIONS_DISCLAIMER
"""

fun userRemovedToPersonRemovedTemplate(
    recipient: String,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that you have been removed the project: $projectTitle.
    
    $NO_NOTIFICATIONS_DISCLAIMER
"""

fun userInvitedTemplate(
    recipient: String,
    invitedUser: String,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that $invitedUser has been invited to $projectTitle.
    
    $NO_NOTIFICATIONS_DISCLAIMER
"""

fun userInvitedToInviteeTemplate(
    recipient: String,
    projectTitle: String
) = """
    Dear $recipient
    
    We write to you to inform you that you have been invited to $projectTitle.
    
    $NO_NOTIFICATIONS_DISCLAIMER
"""

const val NO_NOTIFICATIONS_DISCLAIMER = "If you do not want to receive these notifications per mail, " +
        "you can unsubscribe to none-crucial emails in your personal settings on UCloud"

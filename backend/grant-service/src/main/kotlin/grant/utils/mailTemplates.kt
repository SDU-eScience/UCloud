package dk.sdu.cloud.grant.utils

//TODO()
fun newCommentTemplate(receiver: String, sender: String, projectId: String) =
    """
        <p>Dear $receiver</p>
        <p>You have received a new comment from $sender in project: $projectId. 
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun newIngoingApplicationTemplate(receiver: String, sender: String, projectId: String) =
    """
        <p>Dear $receiver</p>
        <p>You have received a new application from $sender in project: $projectId. 
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

fun responseTemplate(approved: Boolean, receiver: String, sender: String, projectId: String) =
    """
        <p>Dear $receiver</p>
        ${if (approved) approved(projectId) else rejected(projectId)}
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

private fun approved(projectId: String) =
    """
        <p>We are happy to tell you that your application for resources from $projectId has been approved</p>
    """.trimIndent()

private fun rejected(projectId: String) =
    """
        <p>We are sorry, but your application for resources from $projectId has been rejected</p>
    """.trimIndent()

fun updatedTemplate(projectId: String, receiver: String, sender: String) =
    """
        <p>Dear $receiver</p>
        <p>We write to inform you that your applications for resources in project $projectId have been changed by $sender. 
        You might want to check the changes to see if the application still fit your needs.</p>
        $NO_NOTIFICATIONS_DISCLAIMER
    """.trimIndent()

const val NO_NOTIFICATIONS_DISCLAIMER = "<p>If you do not want to receive these notifications per mail, " +
        "you can unsubscribe to none-crucial emails in your personal settings on UCloud</p>"

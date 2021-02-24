package dk.sdu.cloud.accounting.Utils

import dk.sdu.cloud.service.escapeHtml

const val CREDITS_NOTIFY_LIMIT = 5000000
const val LOW_FUNDS_SUBJECT = "Project low on resource"
const val NO_MAIL_TEMPLATE = """<p>If you do not want to receive these email notifications, 
    you can unsubscribe to non-critical emails in your personal settings on UCloud</p>"""

fun stillLowResources (
    recipient: String,
    catagory: String,
    provider: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>
    <p>
        the project ${escapeHtml(projectTitle)} is still low on the ${escapeHtml(catagory)} resource 
        from ${escapeHtml(provider)} after new resources were allocated. <br>
        If this was intentional, please ignore this message.
    </p>
    $NO_MAIL_TEMPLATE
    """.trimIndent()

fun lowResourcesTemplate(
    recipient: String,
    catagory: String,
    provider: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>
    <p>
        the project ${escapeHtml(projectTitle)} is running low on the ${escapeHtml(catagory)} resource 
        from ${escapeHtml(provider)}. <br>
        If needed, you can request additional resources from the project's resource page.
    </p>
    $NO_MAIL_TEMPLATE
    """.trimIndent()


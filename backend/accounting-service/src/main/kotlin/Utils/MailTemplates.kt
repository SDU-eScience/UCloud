package dk.sdu.cloud.accounting.Utils

import dk.sdu.cloud.service.escapeHtml

const val CREDITS_NOTIFY_LIMIT = 5000000
const val LOW_FUNDS_SUBJECT = "Project low on resource"
const val NO_MAIL_TEMPLATE = """<p>If you do not want to receive these notifications per mail, 
    you can unsubscribe to non-crucial emails in your personal settings on UCloud</p>"""

fun stillLowResources (
    recipient: String,
    catagory: String,
    provider: String,
    projectTitle: String
) = """
    <p>Dear ${escapeHtml(recipient)}</p>
    <p>
        We write to you to inform you that the project: ${escapeHtml(projectTitle)} still is low on the 
        ${escapeHtml(catagory)} resource from ${escapeHtml(provider)} after allocation of new resources. <br>
        If this is intentional, then ignore this message.
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
        We write to you to inform you that the project: ${escapeHtml(projectTitle)} is running low on the 
        ${escapeHtml(catagory)} resource from ${escapeHtml(provider)}.
    </p>
    $NO_MAIL_TEMPLATE
    """.trimIndent()


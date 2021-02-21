package dk.sdu.cloud.service

import com.google.common.html.HtmlEscapers

fun escapeHtml(input: String): String {
    @Suppress("UnstableApiUsage")
    return HtmlEscapers.htmlEscaper().escape(input)
}

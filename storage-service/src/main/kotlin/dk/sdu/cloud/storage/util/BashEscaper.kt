package dk.sdu.cloud.storage.util

import com.google.common.escape.Escapers

internal object BashEscaper {
    // We use double-quoted string for the built-in escaping provided by Bash. On top of this we simply need to
    // make sure that user input doesn't escape the string. We should also make sure that variables provided in Bash
    // are not accessible.
    private val bashEscaper =
        Escapers.builder().apply {
            addEscape('\'', "'\"'\"'")
            addEscape('\"', "\\\"")
            addEscape('`', "\\`")
            addEscape('$', "\\$")
        }.build()

    fun safeBashArgument(rawArgument: String) = "\"${bashEscaper.escape(rawArgument)}\""
}
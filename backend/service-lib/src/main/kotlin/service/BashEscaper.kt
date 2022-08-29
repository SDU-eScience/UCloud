package dk.sdu.cloud.service

object BashEscaper {
    // We use double-quoted string for the built-in escaping provided by Bash. On top of this we simply need to
    // make sure that user input doesn't escape the string. We should also make sure that variables provided in Bash
    // are not accessible.
    fun safeBashArgument(rawArgument: String): String {
        val escaped = rawArgument
            .replace("\\", "\\\\")
            .replace("\'", "'\"'\"'")
            .replace("\"", "\\\"")
            .replace("`", "\\`")
            .replace("$", "\\$")
        return "\"$escaped\""
    }
}

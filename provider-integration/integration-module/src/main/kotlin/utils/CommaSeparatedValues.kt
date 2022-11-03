package dk.sdu.cloud.utils

// NOTE(Dan): I really don't want to bring in a library for this. There isn't even a well-defined standard for what
// CSV is supposed to be. So, for the time being we will use this solution, which is based on the following regex:
// https://stackoverflow.com/a/42535295. The regex was modified to also support ';' as the separator since those are
// more common in Denmark. Technically, it now supports mixed delimiters, which I think should be okay.
//
// Right now, this parser will only be able to deal with data which doesn't span multiple lines. This should be fine,
// since none of the things we parse are supposed to have multi-line data anyway. Ideally, we would detect the case of
// missing multi-line data and report an error, but we don't do this at the moment. Instead, the data will become
// corrupt later.
object CommaSeparatedValues {
    fun parse(line: String): List<String> {
        // NOTE(Dan): Excel might potentially include this line in the file. Ignore it.
        if (line.startsWith("sep=")) return emptyList()

        return pattern.findAll(line).map { match ->
            (match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]).replace("\"\"", "\"")
        }.toList()
    }

    // NOTE(Dan): Produces a single line of CSV data. The resulting string will not contain a new-line. If the input
    // contains any values with new-lines then an exception will be thrown.
    fun produce(cells: List<String>): String {
        return buildString {
            for ((index, cell) in cells.withIndex()) {
                if (cell.contains("\n")) error("Cell '$cell' contains new-lines. This is not allowed.")

                if (index != 0) append(",")

                val needsToQuote = cell.contains("\"") || cell.contains(",") || cell.contains(";")
                if (needsToQuote) {
                    append("\"")
                    append(cell.replace("\"", "\"\""))
                    append("\"")
                } else {
                    append(cell)
                }
            }
        }
    }

    private val pattern =
        Regex("(?:[,;]\"|^\")((?:\"\"|[\\w\\W]*?)*?)(?=\"[,;]|\"\$)|(?:[,;](?!\")|^(?!\"))([^,;]*?)(?=\$|[,;])|(\\r\\n|\\n)")
}

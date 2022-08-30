package dk.sdu.cloud.accounting.services.wallets

import kotlin.math.max

fun table(block: TableBuilder.() -> Unit): String {
    return TableBuilder().apply(block).toString()
}

class TableBuilder {
    val cols = ArrayList<Int>()
    val colNames = ArrayList<String>()
    val rows = ArrayList<List<String>>()

    var currRow = ArrayList<String>()

    fun column(header: String, size: Int = 0) {
        cols.add(max((header.length * 1.5).toInt(), size))
        colNames.add(header)
    }

    fun nextRow() {
        if (currRow.isNotEmpty()) {
            rows.add(currRow)
            currRow = ArrayList()
        }
    }

    fun cell(data: Any?) {
        currRow.add(data.toString())
    }

    override fun toString(): String {
        return buildString {
            for ((size, name) in cols.zip(colNames)) {
                append("| ")
                append(name.padEnd(size, ' '))
                append(" | ")
            }
            appendLine()
            for ((size, name) in cols.zip(colNames)) {
                append("| ")
                append(name.padEnd(size, ' ').replace(Regex("."), "-"))
                append(" | ")
            }
            appendLine()

            for (row in rows) {
                for ((index, cell) in row.withIndex()) {
                    append("| ")
                    append(cell.padEnd(cols[index], ' '))
                    append(" | ")
                }
                appendLine()
            }
        }
    }
}

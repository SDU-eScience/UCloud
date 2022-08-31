package dk.sdu.cloud.utils

import dk.sdu.cloud.cli.CommandLineInterface

class TableDsl(private val isParsable: Boolean) {
    private var didSendHeaders = false
    private val headers = ArrayList<Pair<String, Int>>()
    private var currentRow = ArrayList<String>()
    private val message = TerminalMessageDsl()
    private var rowCount = 0

    fun header(name: String, size: Int) {
        headers.add(Pair(name, size))
    }

    fun nextRow() {
        if (!isParsable) {
            if (!didSendHeaders) {
                with(message) {
                    bold {
                        for ((header, size) in headers) {
                            inline(header.padEnd(size))
                        }
                    }
                    line()
                    line(CharArray(120) { '-' }.concatToString())
                }
                didSendHeaders = true
            }

            if (currentRow.isNotEmpty()) {
                rowCount++
                with(message) {
                    for ((index, cell) in currentRow.withIndex()) {
                        val size = headers.getOrNull(index)?.second ?: 0
                        inline(cell.padEnd(size))
                    }
                    line()
                }
            }
            currentRow.clear()
        } else {
            if (currentRow.isNotEmpty()) {
                rowCount++

                message.line(CommaSeparatedValues.produce(currentRow))
            }

            currentRow.clear()
        }
    }

    fun cell(contents: Any?) {
        currentRow.add(contents.toString())
    }

    fun end() {
        with(message) {
            nextRow()
            if (rowCount == 0) line("No data available")
            print()
        }
    }
}

fun CommandLineInterface.sendTerminalTable(builder: TableDsl.() -> Unit) {
    sendTerminalTable(isParsable, builder)
}

fun sendTerminalTable(parsable: Boolean = false, builder: TableDsl.() -> Unit) {
    TableDsl(parsable).also(builder).end()
}

fun CommandLineInterface.sendTerminalSeparator(size: Int = 1) {
    if (isParsable) return
    repeat(size) { println() }
}

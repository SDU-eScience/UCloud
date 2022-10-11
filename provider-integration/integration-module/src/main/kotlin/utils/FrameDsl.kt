package dk.sdu.cloud.utils

import dk.sdu.cloud.cli.CommandLineInterface
import kotlin.math.max

class FrameDsl(private val isParsable: Boolean) {
    var title: String? = null
    var wideTitle = false

    private val fields = ArrayList<Pair<String, String>>()

    fun title(title: String, wide: Boolean = false) {
        this.title = title
        this.wideTitle = wide
    }

    fun field(name: String, content: Any?) {
        fields.add(Pair(name, content.toString()))
    }

    fun divider() {
        fields.add(Pair(DIVIDER, DIVIDER))
    }

    private fun TerminalMessageDsl.sendDivider() {
        val maxLength = fields.maxOfOrNull { it.first.length } ?: return
        val maxContentLength = fields.maxOf { it.second.length }
        val totalLength = if (!wideTitle) max((title?.length ?: 0) + 3, maxLength + maxContentLength + 3) else 120
        line(CharArray(totalLength) { '-' }.concatToString())
    }

    fun send() {
        val maxLength = fields.maxOfOrNull { it.first.length } ?: return
        val maxContentLength = fields.maxOf { it.second.length }
        sendTerminalMessage {
            if (!isParsable) {
                val title = title
                if (title != null) {
                    bold {
                        val totalLength = if (!wideTitle) max(title.length + 3, maxLength + maxContentLength + 3) else 120
                        inline(CharArray((totalLength - title.length) / 2) { ' ' }.concatToString())
                        line(title)
                        sendDivider()
                    }
                }

                for ((name, content) in fields) {
                    if (name == content && name == DIVIDER) {
                        sendDivider()
                        continue
                    }

                    bold {
                        inline(name.padEnd(maxLength + 1))
                        inline("| ")
                    }
                    for ((idx, line) in content.lines().withIndex()) {
                        if (idx != 0) {
                            inline(" ".repeat((maxLength + 1)))
                            inline("| ")
                        }
                        line(line)
                    }
                }
            } else {
                line(CommaSeparatedValues.produce(fields.map { it.second }))
            }
        }
    }

    companion object {
        private const val DIVIDER = "divider"
    }
}

fun CommandLineInterface.sendTerminalFrame(title: String? = null, builder: FrameDsl.() -> Unit) {
    sendTerminalFrame(title, isParsable, builder)
}

fun sendTerminalFrame(title: String? = null, parsable: Boolean = false, builder: FrameDsl.() -> Unit) {
    FrameDsl(parsable).also(builder).also { if (title != null) it.title = title }.send()
}

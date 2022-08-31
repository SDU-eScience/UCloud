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

    fun send() {
        val maxLength = fields.maxOfOrNull { it.first.length } ?: return
        val maxContentLength = fields.maxOf { it.second.length }
        sendTerminalMessage {
            if (!isParsable) {
                val title = title
                if (title != null) {
                    val totalLength = if (!wideTitle) max(title.length + 3, maxLength + maxContentLength + 3) else 120
                    bold {
                        inline(CharArray((totalLength - title.length) / 2) { ' ' }.concatToString())
                        line(title)
                        line(CharArray(totalLength) { '-' }.concatToString())
                    }
                }

                for ((name, content) in fields) {
                    bold {
                        inline(name.padEnd(maxLength + 1))
                        inline("| ")
                    }
                    line(content)
                }
            } else {
                line(CommaSeparatedValues.produce(fields.map { it.second }))
            }
        }
    }
}

fun CommandLineInterface.sendTerminalFrame(title: String? = null, builder: FrameDsl.() -> Unit) {
    sendTerminalFrame(title, isParsable, builder)
}

fun sendTerminalFrame(title: String? = null, parsable: Boolean = false, builder: FrameDsl.() -> Unit) {
    FrameDsl(parsable).also(builder).also { if (title != null) it.title = title }.send()
}

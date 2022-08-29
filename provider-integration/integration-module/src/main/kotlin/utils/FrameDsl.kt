package dk.sdu.cloud.utils

import kotlin.math.max

class FrameDsl {
    var title: String? = null
    private val fields = ArrayList<Pair<String, String>>()

    fun field(name: String, content: Any?) {
        fields.add(Pair(name, content.toString()))
    }

    fun send() {
        val maxLength = fields.maxOfOrNull { it.first.length } ?: return
        val maxContentLength = fields.maxOf { it.second.length }
        sendTerminalMessage {
            val title = title
            if (title != null) {
                val totalLength = max(title.length + 3, maxLength + maxContentLength + 3)
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
        }
    }
}

fun sendTerminalFrame(title: String? = null, builder: FrameDsl.() -> Unit) {
    FrameDsl().also(builder).also { if (title != null) it.title = title }.send()
}

package dk.sdu.cloud.http

import io.ktor.http.*

class HeaderBuilder {
    private val builder = StringBuilder()

    fun add(header: String, value: String) {
        builder.append("$header: $value\r\n")
    }

    fun build(): String = builder.toString()
}

fun HeaderBuilder.contentType(type: ContentType) {
    add("Content-Type", type.toString())
}

fun buildHeaders(addDefault: Boolean = true, builder: HeaderBuilder.() -> Unit): String {
    return HeaderBuilder().apply {
        if (addDefault) {
            add("Server", "UCloud")
            // Add more?
        }
        builder()
    }.build()
}

package dk.sdu.cloud.calls.client

val encodingTableHtml5 = UByteArray(256) { i ->
    val point = Char(i)
    when {
        i in 65..90 -> i.toUByte() // uppercase ascii
        i in 97..122 -> i.toUByte() // lowercase ascii
        i in 48..57 -> i.toUByte() // digit ascii
        point == '*' -> i.toUByte()
        point == '-' -> i.toUByte()
        point == '.' -> i.toUByte()
        point == '_' -> i.toUByte()
        else -> 0u
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
actual fun urlEncode(value: String): String {
    return buildString {
        val encoded = value.encodeToByteArray().toUByteArray()
        for (byte in encoded) {
            val lookup = encodingTableHtml5[byte.toInt()]
            if (lookup != 0.toUByte()) {
                append(Char(lookup.toInt()))
            } else {
                append('%')
                append(byte.toString(16).padStart(2, '0'))
            }
        }
    }
}

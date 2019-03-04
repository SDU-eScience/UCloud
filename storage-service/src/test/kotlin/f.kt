import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.ReclassifyRequest

fun main() {
    println(
        defaultMapper.readValue<ReclassifyRequest>("""{"path":"/home/user3@test.dk/e5773d60-1255-4de5-b448-974bb076c8b7","sensitivity":"null"}""")
    )
}

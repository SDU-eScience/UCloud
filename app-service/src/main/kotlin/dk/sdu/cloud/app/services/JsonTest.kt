package dk.sdu.cloud.app.services

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper

data class SimplifiedJob(
    @JsonAlias("ownerUid")
    val uid: Long
) {
    val ownerUid: Long = uid
}

fun main() {
    println(defaultMapper.writeValueAsString(SimplifiedJob(42)))

    println(
        defaultMapper.readValue<SimplifiedJob>(
            """
            {
                "ownerUid": 42
            }
            """.trimIndent()
        )
    )

    println(
        defaultMapper.readValue<SimplifiedJob>(
            """
            {
                "uid": 42
            }
            """.trimIndent()
        )
    )


    println(
        defaultMapper.readValue<SimplifiedJob>(
            """
            {
                "uid": 42,
                "ownerUid": 42
            }
            """.trimIndent()
        )
    )

    println(
        defaultMapper.readValue<SimplifiedJob>(
            """
            {
                "uid": 42,
                "ownerUid": 50
            }
            """.trimIndent()
        )
    )

        println(
        defaultMapper.readValue<SimplifiedJob>(
            """
            {
                "ownerUid": 50,
                "uid": 42
            }
            """.trimIndent()
        )
    )
}

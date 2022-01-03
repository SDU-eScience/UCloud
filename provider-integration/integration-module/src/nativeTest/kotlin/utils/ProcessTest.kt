package dk.sdu.cloud.utils

import dk.sdu.cloud.utils.startProcessAndCollectToMemory
import kotlin.test.*

class ProcessTest {
    @Test
    fun `test starting a simple process`() {
        val collected = startProcessAndCollectToMemory(
            listOf(
                "/usr/bin/env",
                "bash",
                "-c",
                "echo \"Hello, World!\""
            )
        )

        assertEquals(0, collected.statusCode)
        assertEquals("Hello, World!\n", collected.stdout.decodeToString())
        assertEquals(0, collected.stderr.size)
    }

    @Test
    fun `test exit code`() {
        val collected = startProcessAndCollectToMemory(
            listOf(
                "/usr/bin/env",
                "bash",
                "-c",
                "exit 42"
            )
        )

        assertEquals(42, collected.statusCode)
    }

    @Test
    fun `test long output`() {
        val collected = startProcessAndCollectToMemory(
            listOf(
                "/usr/bin/env",
                "bash",
                "-c",
                "cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 50000 | head -n 1 "
            )
        )

        assertEquals(0, collected.statusCode)
        assertEquals(50001, collected.stdout.size)
        assertEquals(0, collected.stderr.size)
    }

    @Test
    fun `test waiting around for a bit`() {
        val collected = startProcessAndCollectToMemory(
            listOf(
                "/usr/bin/env",
                "bash",
                "-c",
                "sleep 2 ; echo Hi!"
            )
        )

        assertEquals(0, collected.statusCode)
        assertEquals("Hi!\n", collected.stdout.decodeToString())
    }
}

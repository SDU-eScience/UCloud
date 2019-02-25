package dk.sdu.cloud.app.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `Create simple Input App Param`() {
        val input = ApplicationParameter.InputDirectory(
            "name",
            false,
            FileTransferDescription(
                "source",
                "destination/"
            ),
            "title",
            "description"
        )

        assertEquals("name", input.name)
        assertEquals("description", input.description)
        assertEquals("title", input.title)
        assertEquals("input_directory", input.type)
        assertEquals(false, input.optional)
        assertEquals("source", input.defaultValue?.source)
        assertEquals("destination/", input.defaultValue?.destination)

        input.toInvocationArgument(input.defaultValue!!)

        assertEquals("destination/", input.defaultValue?.destination)

        val results = input.map(mapOf(Pair("source", "anotherSource"), Pair("destination", "anotherDestination")))
        assertEquals("anotherSource", results?.source)
        assertEquals("anotherDestination", results?.destination)

    }

    @Test
    fun `Create simple Integer App Param`() {
        val app = ApplicationParameter.Integer(
            "name",
            false,
            IntApplicationParameter(2.toBigInteger()),
            "title",
            "description",
            1.toBigInteger(),
            2.toBigInteger(),
            0.toBigInteger(),
            "unitName"
        )

        assertEquals("name", app.name)
        assertEquals("description", app.description)
        assertEquals("title", app.title)
        assertEquals("integer", app.type)
        assertEquals(false, app.optional)
        assertEquals("unitName", app.unitName)
        assertEquals(2.toBigInteger(), app.defaultValue?.value)
        assertEquals(2.toBigInteger(), app.max)
        assertEquals(1.toBigInteger(), app.min)
        assertEquals(0.toBigInteger(), app.step)

        val result = app.map(3)
        assertEquals(3.toBigInteger(), result?.value)

        val arg = app.toInvocationArgument(IntApplicationParameter(10.toBigInteger()))
        assertEquals("10", arg)
    }

    @Test
    fun `Create simple Double App Param`() {
        val app = ApplicationParameter.FloatingPoint(
            "name",
            false,
            DoubleApplicationParameter(2.2.toBigDecimal()),
            "title",
            "description",
            1.1.toBigDecimal(),
            2.2.toBigDecimal(),
            0.0.toBigDecimal(),
            "unitName"
        )

        assertEquals("name", app.name)
        assertEquals("description", app.description)
        assertEquals("title", app.title)
        assertEquals("floating_point", app.type)
        assertEquals(false, app.optional)
        assertEquals("unitName", app.unitName)
        assertEquals(2.2.toBigDecimal(), app.defaultValue?.value)
        assertEquals(2.2.toBigDecimal(), app.max)
        assertEquals(1.1.toBigDecimal(), app.min)
        assertEquals(0.0.toBigDecimal(), app.step)

        val result = app.map(3.toBigDecimal())!!
        val diff = 3.0.toBigDecimal() - result.value
        assertTrue(diff < 0.001.toBigDecimal())

        val arg = app.toInvocationArgument(DoubleApplicationParameter(10.11.toBigDecimal()))
        assertEquals("10.11", arg)
    }
}

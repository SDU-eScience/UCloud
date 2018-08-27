package dk.sdu.cloud.app.api

import org.junit.Test
import kotlin.test.assertEquals

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
            2,
            "title",
            "description",
            1,
            2,
            0,
            "unitName"
        )

        assertEquals("name", app.name)
        assertEquals("description", app.description)
        assertEquals("title", app.title)
        assertEquals("integer", app.type)
        assertEquals(false, app.optional)
        assertEquals("unitName", app.unitName)
        assertEquals(2, app.defaultValue)
        assertEquals(2, app.max)
        assertEquals(1, app.min)
        assertEquals(0, app.step)

        val result = app.map(3)
        assertEquals(3, result)

        val arg = app.toInvocationArgument(10)
        assertEquals("10", arg)
    }

    @Test
    fun `Create simple Double App Param`() {
        val app = ApplicationParameter.FloatingPoint(
            "name",
            false,
            2.2,
            "title",
            "description",
            1.1,
            2.2,
            0.0,
            "unitName"
        )

        assertEquals("name", app.name)
        assertEquals("description", app.description)
        assertEquals("title", app.title)
        assertEquals("floating_point", app.type)
        assertEquals(false, app.optional)
        assertEquals("unitName", app.unitName)
        assertEquals(2.2, app.defaultValue)
        assertEquals(2.2, app.max)
        assertEquals(1.1, app.min)
        assertEquals(0.0, app.step)

        val result = app.map(3)
        assertEquals(3.0, result)

        val arg = app.toInvocationArgument(10.11)
        assertEquals("10.11", arg)
    }
}
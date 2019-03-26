package dk.sdu.cloud.app.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `Create simple Input directory App Param`() {
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
    fun `Create simple Input file App Param`() {
        val input = ApplicationParameter.InputFile(
            "inputFile",
            false,
            FileTransferDescription(
                "source",
                "destination"),
            "title",
            "description"
        )

        assertEquals("inputFile", input.name)
        assertEquals(false, input.optional)
        assertEquals("title", input.title)
        assertEquals("description", input.description)
        assertEquals("source", input.defaultValue?.source)
        assertEquals("destination", input.defaultValue?.destination)

        input.toInvocationArgument(input.defaultValue!!)

        assertEquals("destination", input.defaultValue?.destination)
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

    @Test
    fun `Create simple Text App Param`() {
        val textInput = ApplicationParameter.Text(
            "textName",
            false,
            StringApplicationParameter("Content"),
            "title",
            "description"
        )

        assertEquals(false, textInput.optional)
        assertEquals("text", textInput.type)
        assertEquals("title", textInput.title)
        assertEquals("description", textInput.description)


        val arg = textInput.toInvocationArgument(textInput.defaultValue!!)
        assertEquals("Content", arg)
    }

    @Test
    fun `Create simple Bool App Param`() {
        val boolParam = ApplicationParameter.Bool(
            "boolName",
            true,
            BooleanApplicationParameter(true),
            "title",
            "description",
            "veryMuchYes!",
            "notSoMuchNo"
        )

        assertEquals( "boolName", boolParam.name)
        assertEquals(true, boolParam.optional)
        assertEquals("title", boolParam.title)
        assertEquals("description", boolParam.description)
        assertEquals("veryMuchYes!", boolParam.trueValue)
        assertEquals("notSoMuchNo", boolParam.falseValue)

        val arg = boolParam.toInvocationArgument(boolParam.defaultValue!!)
        assertEquals("veryMuchYes!", arg)
    }
}

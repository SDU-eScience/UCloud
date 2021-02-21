package dk.sdu.cloud.app.store.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `Create simple Input directory App Param`() {
        val input = ApplicationParameter.InputDirectory(
            "name",
            false,
            null,
            "title",
            "description"
        )

        assertEquals("name", input.name)
        assertEquals("description", input.description)
        assertEquals("title", input.title)
        assertEquals("input_directory", input.type)
        assertEquals(false, input.optional)
    }

    @Test
    fun `Create simple Integer App Param`() {
        val app = ApplicationParameter.Integer(
            "name",
            false,
            null,
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
        assertEquals(2.toBigInteger(), app.max)
        assertEquals(1.toBigInteger(), app.min)
        assertEquals(0.toBigInteger(), app.step)
    }

    @Test
    fun `Create simple Double App Param`() {
        val app = ApplicationParameter.FloatingPoint(
            "name",
            false,
            null,
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
        assertEquals(2.2.toBigDecimal(), app.max)
        assertEquals(1.1.toBigDecimal(), app.min)
        assertEquals(0.0.toBigDecimal(), app.step)
    }

    @Test
    fun `Create simple Text App Param`() {
        val textInput = ApplicationParameter.Text(
            "textName",
            false,
            null,
            "title",
            "description"
        )

        assertEquals(false, textInput.optional)
        assertEquals("text", textInput.type)
        assertEquals("title", textInput.title)
        assertEquals("description", textInput.description)
    }

    @Test
    fun `Create simple Enum App Param`() {
        val enum = ApplicationParameter.Enumeration(
            "enumName",
            false,
            null,
            "title",
            "description",
            listOf(ApplicationParameter.EnumOption("Bash", "0"), ApplicationParameter.EnumOption("Fish", "1"))
        )

        assertEquals(false, enum.optional)
        assertEquals("enumeration", enum.type)
        assertEquals("title", enum.title)
        assertEquals("description", enum.description)
    }

    @Test
    fun `Create simple Bool App Param`() {
        val boolParam = ApplicationParameter.Bool(
            "boolName",
            true,
            null,
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
    }
}

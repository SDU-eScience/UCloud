package dk.sdu.cloud.app.api

import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class AppsAndToolsTest{

    @Test
    fun `create simple V1 Application description with string invocation`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf("string"),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs")
        )

        assertEquals("name@2.2", v1.tool.toString())
        assertEquals("name", v1.name)
        assertEquals("2.2", v1.version)
        assertEquals("title", v1.title)
        assertEquals("name", v1.tool.name)
        assertEquals("2.2", v1.tool.version)
        assertEquals("Authors", v1.authors.first())
        assertEquals("title", v1.title)
        assertEquals("description", v1.description)
        assertEquals("string", v1.parameters.keys.first())
        assertEquals("globs", v1.outputFileGlobs.first())
        assertEquals("string", v1.invocation.first().toString())

        val normTool = v1.normalize()

        assertEquals("name", normTool.info.name)
        assertEquals("2.2", normTool.info.version)
        assertEquals("title", normTool.title)
        assertEquals("description", normTool.description)
        assertEquals("Authors", normTool.authors.first())
        assertEquals("globs", normTool.outputFileGlobs.first().toString())
        assertEquals("name", normTool.tool.name)
        assertEquals("2.2", normTool.tool.version)

    }

    @Test
    fun `create simple V1 Application description with int invocation`() {
        ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(2),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs")
        )
    }

    @Test
    fun `create simple V1 Application description with var invocation`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "var"), Pair("vars", "hello"))),
            mapOf(Pair("hello", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description with var invocation - missing var`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "var"))),
            mapOf(Pair("hello", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }

    @Test
    fun `create simple V1 Application description with flag invocation`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "flag"), Pair("var", "hello"), Pair("flag", "true"))),
            mapOf(Pair("hello", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }


    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description - not correct type`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "NO"))),
            mapOf(Pair("hello", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description with flag invocation - missing var`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "flag"))),
            mapOf(Pair("hello", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description with flag invocation - missing flag`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "flag"), Pair("var", "hello"))),
            mapOf(Pair("hello", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description with var invocation - missin type tag`() {
        ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("notType", "var"))),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException::class)
    fun `create simple V1 Application description with missing params`() {
        val v1 = ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(mapOf(Pair("type", "var"), Pair("vars", "hello"))),
            mapOf(Pair("string", ApplicationParameter.Bool("hello") )),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException.DuplicateDefinition::class)
    fun `create simple V1 Application description with duplicate glob`() {
        ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(2),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs", "globs")
        )
    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description with bad invocation`() {
        ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf(Pair("type", "var")),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs")
        )
    }

    @Test
    fun `create simple V1 tool description`() {
        val v1 = ToolDescription.V1(
            "name",
            "2.2",
            "title",
            "container",
            ToolBackend.SINGULARITY,
            listOf("Authors"),
            2,
            2,
            SimpleDuration(1, 2, 30),
            listOf("modules"),
            "description"
        )

        assertEquals("name", v1.name)
        assertEquals("2.2", v1.version)
        assertEquals("title", v1.title)
        assertEquals("container", v1.container)
        assertEquals(ToolBackend.SINGULARITY, v1.backend)
        assertEquals("Authors", v1.authors.first())
        assertEquals(2, v1.defaultNumberOfNodes)
        assertEquals(2, v1.defaultTasksPerNode)
        assertEquals(1, v1.defaultMaxTime.hours)
        assertEquals(2, v1.defaultMaxTime.minutes)
        assertEquals(30, v1.defaultMaxTime.seconds)
        assertEquals("modules", v1.requiredModules.first())
        assertEquals("description", v1.description)

        val normTool = v1.normalize()

        assertEquals("name", normTool.info.name)
        assertEquals("2.2", normTool.info.version)
        assertEquals("title", normTool.title)
        assertEquals("container", normTool.container)
        assertEquals(ToolBackend.SINGULARITY, normTool.backend)
        assertEquals("Authors", normTool.authors.first())
        assertEquals(2, normTool.defaultNumberOfNodes)
        assertEquals(2, normTool.defaultTasksPerNode)
        assertEquals(1, normTool.defaultMaxTime.hours)
        assertEquals(2, normTool.defaultMaxTime.minutes)
        assertEquals(30, normTool.defaultMaxTime.seconds)
        assertEquals("modules", normTool.requiredModules.first())
        assertEquals("description", normTool.description)

    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description - Too long name`() {
        ApplicationDescription.V1(
            "name".repeat(1000),
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors"),
            "title",
            "description",
            listOf("string"),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs")
        )
    }

    @Test (expected = ApplicationVerificationException.BadValue::class)
    fun `create simple V1 Application description - bad author`() {
        ApplicationDescription.V1(
            "name",
            "2.2",
            NameAndVersion("name", "2.2"),
            listOf("Authors\n", "good author"),
            "title",
            "description",
            listOf("string"),
            mapOf(Pair("string", mockk(relaxed = true))),
            listOf("globs")
        )
    }

    @Test (expected = ToolVerificationException.BadValue::class)
    fun `create simple V1 tool description - throws exception (to long name)`() {
        ToolDescription.V1(
            "name".repeat(1000),
            "2.2",
            "title",
            "container",
            ToolBackend.SINGULARITY,
            listOf("Authors"),
            2,
            2,
            SimpleDuration(1, 2, 30),
            listOf("modules"),
            "description"
        )

    }

    @Test (expected = ToolVerificationException.BadValue::class)
    fun `create simple V1 tool description - newline in version`() {
        ToolDescription.V1(
            "name",
            "2.2\n",
            "title",
            "container",
            ToolBackend.SINGULARITY,
            listOf("Authors"),
            2,
            2,
            SimpleDuration(1, 2, 30),
            listOf("modules"),
            "description"
        )

    }

    @Test (expected = ToolVerificationException.BadValue::class)
    fun `create simple V1 tool description - bad authors`() {
        ToolDescription.V1(
            "name",
            "2.2",
            "title",
            "container",
            ToolBackend.SINGULARITY,
            listOf("Authors\n", "author"),
            2,
            2,
            SimpleDuration(1, 2, 30),
            listOf("modules"),
            "description"
        )

    }
}
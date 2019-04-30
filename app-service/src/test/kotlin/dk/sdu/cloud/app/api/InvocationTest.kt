package dk.sdu.cloud.app.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvocationTest {

    @Test
    fun `Build invocation test`() {
        val invocation = VariableInvocationParameter(
            listOf("variable1", "variable2")
        )

        val buildInvocation = invocation.buildInvocationSnippet(
            mapOf(
                ApplicationParameter.Integer("variable1") to IntApplicationParameter(1.toBigInteger()),
                ApplicationParameter.Text("variable2") to StringApplicationParameter("Text")
            )
        )

        assertEquals("\"1\" \"Text\"",buildInvocation)
    }

    @Test
    fun `Build invocation test - with all prefix and suffix`() {
        val invocation = VariableInvocationParameter(
            listOf("variable1", "variable2"),
            "prefixGlobal",
            "suffixGlobal",
            "prefixVar",
            "suffixVar"
        )

        val buildInvocation = invocation.buildInvocationSnippet(
            mapOf(
                ApplicationParameter.Integer("variable1") to IntApplicationParameter(1.toBigInteger()),
                ApplicationParameter.Text("variable2") to StringApplicationParameter("Text")
            )
        )
        assertEquals("\"prefixGlobal\" \"prefixVar\" \"1\" \"suffixVar\" \"prefixVar\" \"Text\" \"suffixVar\" \"suffixGlobal\"", buildInvocation)
    }

    @Test
    fun `Build invocation test - field to value is empty`() {
        val invocation = VariableInvocationParameter(
            listOf("variable1", "variable2")
        )

        invocation.buildInvocationSnippet(
            mapOf(
                ApplicationParameter.Integer() to IntApplicationParameter(1.toBigInteger()),
                ApplicationParameter.Text() to StringApplicationParameter("Text")
            )
        )
    }

    @Test
    fun `boolean flag test`() {
        val boolFlag = BooleanFlagParameter(
            "Variable",
            "true"
        )

        val result = boolFlag.buildInvocationSnippet(
            mapOf(
                ApplicationParameter.Bool("Variable") to BooleanApplicationParameter(true)
            )
        )

        assertEquals("true", result)
    }

    @Test
    fun `boolean flag test - invalid param usage`() {
        val boolFlag = BooleanFlagParameter(
            "Variable",
            "True"
        )
        try {
            boolFlag.buildInvocationSnippet(
                mapOf(
                    ApplicationParameter.Bool("Variable") to IntApplicationParameter(1.toBigInteger())
                )
            )
        } catch (ex: Exception) {
            assertEquals("Invalid type", ex.message)
            return
        }
        assertTrue(false)
    }

    @Test
    fun `boolean flag test - no params`() {
        val boolFlag = BooleanFlagParameter(
            "Variable",
            "True"
        )

        assertNull(boolFlag.buildInvocationSnippet(emptyMap()))
    }

    @Test
    fun `test word invocation`() {
        val word = WordInvocationParameter("this")
        assertEquals("this", word.buildInvocationSnippet(emptyMap()))
    }
}

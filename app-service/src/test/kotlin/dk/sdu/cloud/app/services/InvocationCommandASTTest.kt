package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppParametersWithValues
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.BooleanApplicationParameter
import dk.sdu.cloud.app.api.BooleanFlagParameter
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvocationCommandASTTest {
    @Test
    fun `Boolean flag test`() {
        val bo = BooleanFlagParameter("variable", "This is the flag")
        val m: AppParametersWithValues = mapOf(
            Pair(ApplicationParameter.Bool("variable"), BooleanApplicationParameter(true))
        )
        assertEquals("This is the flag", bo.buildInvocationSnippet(m))
        assertEquals("This is the flag", bo.flag)
        assertEquals("variable", bo.variableName)
    }

    @Test
    fun `Boolean flag test - no entry`() {
        val bo = BooleanFlagParameter("variable", "This is the flag")
        val m: AppParametersWithValues = mapOf(
            Pair(ApplicationParameter.Bool("not what we are looking for"), BooleanApplicationParameter(true))
        )
        assertNull(bo.buildInvocationSnippet(m))
    }

    @Test(expected = RuntimeException::class)
    fun `Boolean flag test - invalid param`() {
        val bo = BooleanFlagParameter("variable", "flag")
        val m: AppParametersWithValues = mapOf(
            Pair(ApplicationParameter.Text("variable"), mockk(relaxed = true))
        )
        bo.buildInvocationSnippet(m)
    }
}

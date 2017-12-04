package org.escienceclud.abc

import org.esciencecloud.abc.services.MixedNode
import org.esciencecloud.abc.services.TemplateParser
import org.esciencecloud.abc.services.VariableNode
import org.esciencecloud.abc.services.WordNode
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateParserTest {
    @Test(expected = IllegalArgumentException::class)
    fun testMultiLineTemplate() {
        val template = "ls bad stuff goes here \n\n\n\n\r\n\r\n 123"
        TemplateParser().parseSingleLineTemplate(template)
    }

    @Test
    fun testSimpleWords() {
        val template = "a word and more words this is okay123 123 !@#@#@"
        val nodes = TemplateParser().parseSingleLineTemplate(template)
        assertEquals(10, nodes.size)

        assertEquals(WordNode("a"), nodes[0])
        assertEquals(WordNode("word"), nodes[1])
        assertEquals(WordNode("and"), nodes[2])
        assertEquals(WordNode("more"), nodes[3])
        assertEquals(WordNode("words"), nodes[4])
        assertEquals(WordNode("this"), nodes[5])
        assertEquals(WordNode("is"), nodes[6])
        assertEquals(WordNode("okay123"), nodes[7])
        assertEquals(WordNode("123"), nodes[8])
        assertEquals(WordNode("!@#@#@"), nodes[9])
    }

    @Test
    fun testVariableAtEndValid() {
        run {
            val nodes = TemplateParser().parseSingleLineTemplate("foo $2")
            assertEquals(VariableNode("2"), nodes[1])
        }

        run {
            val nodes = TemplateParser().parseSingleLineTemplate("foo \${23}")
            assertEquals(VariableNode("23"), nodes[1])
        }

        run {
            val nodes = TemplateParser().parseSingleLineTemplate("foo $")
            assertEquals(WordNode("$"), nodes[1])
        }
    }

    @Test(expected = IllegalStateException::class)
    fun testVariableAtEndInvalid() {
        TemplateParser().parseSingleLineTemplate("foo \${")
    }

    @Test(expected = IllegalStateException::class)
    fun testInvalidEscapeAtEnd() {
        TemplateParser().parseSingleLineTemplate("foo \\")
    }

    @Test
    fun testSideBySideVariables() {
        val nodes = TemplateParser().parseSingleLineTemplate("\${foo}\$bar")
        assertEquals(MixedNode(listOf(VariableNode("foo"), VariableNode("bar"))), nodes[0])
        assertEquals(1, nodes.size)
    }

    @Test
    fun testVariableNextToWord() {
        val nodes = TemplateParser().parseSingleLineTemplate("foo\$bar")
        assertEquals(MixedNode(listOf(WordNode("foo"), VariableNode("bar"))), nodes[0])
    }
}
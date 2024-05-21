package dk.sdu.cloud.accounting.services.accounting

import kotlin.test.*

class MermaidTest {
    @Test
    fun `simple test`() {
        // NOTE(Dan): I don't have any good way of automatically testing the validity of this. But we can at the
        // very least make sure that it runs.

        mermaid {
            var a2 = ""

            val g1 = subgraph("one", "One") {
                val a1 = node("a1", "A1")
                a2 = node("a2", "A2")
                a1.linkTo(a2, "Some text")
            }

            val g2 = subgraph("two", "Two") {
                val b1 = node("b1")
                val b2 = node("b2")
                b1.linkTo(b2, lineType = MermaidGraphBuilder.LineType.DOTTED)
            }

            val g3 = subgraph("three", "Three") {
                val c1 = node("c1")
                val c2 = node("c2")
                c1.linkTo(a2)
                c1.linkTo(c2)
            }

            g1.linkTo(g2, "Graph link!")
        }// .also { println(it) }
    }
}

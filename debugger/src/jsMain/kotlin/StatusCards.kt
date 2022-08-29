package dk.sdu.cloud.debug

import kotlinx.browser.document
import kotlinx.html.TBODY
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import org.w3c.dom.HTMLElement
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class StatusCards(private val client: Client) {
    private lateinit var elem: HTMLElement
    private val card = Card().also { it.render() }
    private val card2 = Card().also { it.render() }
    private val card3 = Card().also { it.render() }
    private var interests = emptyList<String>()

    fun render(): HTMLElement {
        style.mount()

        elem = document.create.div(elemClass)
        elem.appendChild(card.render())
        elem.appendChild(card2.render())
        elem.appendChild(card3.render())
        return elem
    }

    fun updateStats(stats: ServerToClient.Statistics) {
        val card = when (interests.indexOf(stats.servicePath)) {
            0 -> card
            1 -> card2
            2 -> card3
            else -> return
        }

        card.title(stats.servicePath)
        card.content(document.create.div {
            div { inlineStyle { height = 8.px } }

            table {
                thead {
                    tr {
                        th { }
                        th { +"Avg" }
                        th { +"P50" }
                        th { +"P99" }
                        th { +"OK" }
                    }
                }

                tbody {
                    tableRowForStats("Client", stats.client)
                    tableRowForStats("Server", stats.server)
                }
            }

            div { inlineStyle { height = 28.px } }

            table {
                thead {
                    tr {
                        th {
                            inlineStyle { width = 33.percent }
                            +"Everyt."
                        }
                        th {
                            inlineStyle { width = 33.percent }
                            +"Detail"
                        }
                        th {
                            inlineStyle { width = 33.percent }
                            +"Normal"
                        }
                    }
                }

                tbody {
                    tr {
                        td { text(stats.logs.everything) }
                        td { text(stats.logs.details) }
                        td { text(stats.logs.normal) }
                    }
                }
            }

            div { inlineStyle { height = 8.px } }

            table {
                thead {
                    tr {
                        th {
                            inlineStyle { width = 33.percent }
                            +"Odd"
                        }
                        th {
                            inlineStyle { width = 33.percent }
                            +"Wrong"
                        }
                        th {
                            inlineStyle { width = 33.percent }
                            +"Danger"
                        }
                    }
                }

                tbody {
                    tr {
                        td { text(stats.logs.odd) }
                        td { text(stats.logs.wrong) }
                        td { text(stats.logs.dangerous) }
                    }
                }
            }
        })
    }

    private fun TBODY.tableRowForStats(name: String, stats: ServerToClient.Statistics.ResponseStats) {
        tr {
            th { text(name) }
            td { text(stats.avg.milliseconds.toString(DurationUnit.MILLISECONDS, 2)) }
            td { text(stats.p50.milliseconds.toString(DurationUnit.MILLISECONDS, 2)) }
            td { text(stats.p99.milliseconds.toString(DurationUnit.MILLISECONDS, 2)) }
            val successRate = if (stats.count == 0L) 100.0 else (stats.successes / stats.count.toDouble()) * 100
            td { text("${successRate.asDynamic().toFixed(2)}%") }
        }
    }

    fun updateInterests(interests: List<String>) {
        this.interests = interests
        client.send(ClientToServer.UpdateInterests(interests))
    }

    companion object {
        private const val elemClass = "status-cards"

        private val style = CssMounter {
            (byClass(elemClass)) {
                height = 250.px
                display = "flex"
                flexDirection = "row"
                gap = 16.px
            }

            (byClass(elemClass) descendant byTag("table")) {
                width = 100.percent
                textAlign = "left"
            }
        }
    }
}

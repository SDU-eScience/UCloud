package dk.sdu.cloud.debug

import dk.sdu.cluod.debug.defaultMapper
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.serialization.encodeToString
import org.w3c.dom.Element
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class DataViewer {
    lateinit var elem: HTMLElement
    private val metadataRef = DomRef<HTMLDivElement>()
    private val jsonViewerRef = DomRef<HTMLDivElement>()

    fun render(): HTMLElement {
        style.mount()
        if (this::elem.isInitialized) return elem
        elem = document.create.div(elemClass) {
            div {
                capture(metadataRef)
            }

            div {
                inlineStyle {
                    marginTop = 16.px
                }
                capture(jsonViewerRef)
            }
        }
        return elem
    }

    fun update(row: DebugMessage) {
        val metadata = metadataRef.find(elem)
        metadata.innerHTML = ""
        val openQueryRef = DomRef<HTMLAnchorElement>()
        metadata.append {
            table {
                tr {
                    th { +"Timestamp" }
                    td {
                        code {
                            text(buildString {
                                with(Date(row.timestamp)) {
                                    append(getUTCDate().toString().padStart(2, '0'))
                                    append(' ')
                                    append(
                                        when (getUTCMonth() + 1) {
                                            1 -> "Jan"
                                            2 -> "Feb"
                                            3 -> "Mar"
                                            4 -> "Apr"
                                            5 -> "May"
                                            6 -> "Jun"
                                            7 -> "Jul"
                                            8 -> "Aug"
                                            9 -> "Sep"
                                            10 -> "Oct"
                                            11 -> "Nov"
                                            12 -> "Dec"
                                            else -> "???"
                                        }
                                    )
                                    append(' ')
                                    append(getUTCFullYear().toString().padStart(4, '0'))
                                    append(' ')
                                    append(getUTCHours().toString().padStart(2, '0'))
                                    append(':')
                                    append(getUTCMinutes().toString().padStart(2, '0'))
                                    append(':')
                                    append(getUTCSeconds().toString().padStart(2, '0'))
                                    append(':')
                                    append(getUTCMilliseconds().toString().padStart(3, '0'))
                                    append(" UTC")
                                }
                            })
                        }
                    }
                }

                tr {
                    th { +"Message" }
                    td { code { +LogRow.message(row) } }
                }

                tr {
                    th { +"Debug ID" }
                    td { code { +row.context.id } }
                }

                if (row is DebugMessage.WithCall) {
                    tr {
                        th { +"Call" }
                        td { code { +(row.call ?: "null") } }
                    }
                }

                if (row is DebugMessage.WithResponseCode) {
                    tr {
                        th { +"Response" }
                        td { code { text(row.responseCode) } }
                    }
                }

                if (row is DebugMessage.WithResponseTime) {
                    tr {
                        th { +"Response Time" }
                        td { code { text(row.responseTime.milliseconds.toString()) } }
                    }
                }

                if (row is DebugMessage.DatabaseQuery) {
                    tr {
                        th { +"Query" }
                        td {
                            a("javascript:void(0)") {
                                capture(openQueryRef)
                                text("Open")
                            }
                        }
                    }
                }
            }
        }

        openQueryRef.findOrNull(metadata)?.onclick = {
            openDatabaseQuery(row as DebugMessage.DatabaseQuery)
        }

        val jsonPayload = when (row) {
            is DebugMessage.ClientRequest -> defaultMapper.encodeToString(row.payload)
            is DebugMessage.ClientResponse -> defaultMapper.encodeToString(row.response)
            is DebugMessage.Log -> if (row.extras != null) defaultMapper.encodeToString(row.extras) else null
            is DebugMessage.ServerRequest -> defaultMapper.encodeToString(row.payload)
            is DebugMessage.ServerResponse -> defaultMapper.encodeToString(row.response)
            else -> null
        }

        val payloadHeading = when (row) {
            is DebugMessage.ClientRequest -> "Request"
            is DebugMessage.ClientResponse -> "Response"
            is DebugMessage.Log -> "Data"
            is DebugMessage.ServerRequest -> "Request"
            is DebugMessage.ServerResponse -> "Response"
            else -> null
        }

        val jsonContainer = jsonViewerRef.find(elem)
        jsonContainer.innerHTML = ""
        if (jsonPayload != null) {
            BigJsonViewerDom.fromData(jsonPayload).then {
                val node = it.getRootElement()
                node.asDynamic().openAll(2)
                jsonContainer.append {
                    b { text(payloadHeading ?: "Data") }
                }
                jsonContainer.appendChild(node)
            }
        }
    }

    fun openDatabaseQuery(dbQuery: DebugMessage.DatabaseQuery) {
        val targetWindow = window.open("/popup", "_blank", "popup,width=1600,height=900")
        targetWindow?.addEventListener("DOMContentLoaded", {
            val head = targetWindow.document.head!!
            val body = targetWindow.document.body!!

            // Styles
            head.appendChild(createGlobalStyle())
            head.appendChild(document.create.css {
                (byTag("msc-code-viewer")) {
                    setVariable(CSSVar("msc-code-viewer-border-radius"), 5.px)
                    flexGrow = "1"

                    // NOTE(Dan): This completely defeats the purpose of flexbox, but I cannot be bothered with
                    // writing good styling for this.
                    maxWidth = "calc(100vw - 500px)"
                }

                (byClass("wrapper")) {
                    display = "flex"
                    gap = 16.px
                }
            })

            // Dependencies
            body.appendChild(
                document.create.script("module", "/static/msc-code-viewer/wc-msc-code-viewer.js") {}
            )

            // User interface
            val wrapper = document.createElement("div")
            wrapper.classList.add("wrapper")
            body.appendChild(wrapper)

            document.createElement("msc-code-viewer").apply {
                wrapper.appendChild(this)
                val query = run {
                    val rawQuery = dbQuery.query
                    val queryEstimatedIndent = run {
                        val secondLine = rawQuery.lines().getOrNull(1)
                        if (secondLine == null) {
                            0
                        } else {
                            var count = 0
                            for (char in secondLine) {
                                if (char.isWhitespace()) count++
                                else break
                            }

                            count
                        }
                    }

                    if (queryEstimatedIndent == 0) {
                        rawQuery
                    } else {
                        buildString {
                            val prefix = CharArray(queryEstimatedIndent) { ' ' }.concatToString()
                            val lines = rawQuery.lineSequence()
                            for (line in lines) {
                                appendLine(line.removePrefix(prefix))
                            }
                        }
                    }
                }

                textContent = query
            }

            document.createElement("div").let { jsonContainer ->
                jsonContainer as HTMLDivElement
                wrapper.appendChild(jsonContainer)
                jsonContainer.style.flexBasis = 500.px

                BigJsonViewerDom.fromData(defaultMapper.encodeToString(dbQuery.parameters)).then {
                    val node = it.getRootElement()
                    node.asDynamic().openAll(2)
                    jsonContainer.appendChild(node)
                }
            }
        })
    }

    companion object {
        private const val elemClass = "data-viewer"
        val style = CssMounter {
            val self = byClass(elemClass)
            self {

            }

            (self descendant byTag("table")) {
                width = 100.percent

                borderCollapse = "separate"
                borderSpacing = "0 13px"
            }

            (self descendant byTag("th")) {
                textAlign = "left"
                width = 100.px
                verticalAlign = "top"
            }

            (self descendant byTag("td")) {
                textAlign = "left"
            }
        }
    }
}

package dk.sdu.cloud.debug

import dk.sdu.cluod.debug.defaultMapper
import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.dom.create
import kotlinx.serialization.encodeToString
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date

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
        metadata.append {
            table {
                tr {
                    th { +"Timestamp" }
                    td { code { text(Date(row.timestamp).toUTCString()) } }
                }

                tr {
                    th { +"Message" }
                    td { code { +LogRow.message(row) } }
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
            }
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

    companion object {
        private const val elemClass = "data-viewer"
        val style = CssMounter {
            val self = byClass(elemClass)
            self {

            }

            (self descendant byTag("table")) {
                width = 100.percent
            }

            (self descendant byTag("th")) {
                textAlign = "left"
                width = 100.px
            }

            (self descendant byTag("td")) {
                textAlign = "left"
            }
        }
    }
}

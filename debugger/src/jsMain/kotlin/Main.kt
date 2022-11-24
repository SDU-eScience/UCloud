package dk.sdu.cloud.debug

import kotlinx.browser.window
import kotlinx.browser.document
import kotlinx.html.dom.create
import kotlinx.html.js.div
import org.w3c.dom.HTMLElement

val body inline get() = document.body!!
val head inline get() = document.head!!

fun createGlobalStyle(): HTMLElement  {
    return document.create.css {
        importGoogleFont("Inter", listOf(100, 400, 700))
        importGoogleFont("JetBrains Mono", listOf(100, 400, 700))

        (byTag("html")) {
            fontFamily = "'Inter', sans-serif"
        }

        (byTag("code")) {
            fontFamily = "'JetBrains Mono', monospace"
        }

        (byTag("h3")) {
            cursor = "inherit"
            fontSize = 24.px
            margin = 0.px
            fontWeight = "400"
        }
    }
}

fun main() {
    if (window.location.pathname.contains("/popup")) return
    cssReset()
    head.append(createGlobalStyle())

    val client = Client()

    val header = Header()
    body.appendChild(header.render())

    val sidebar = Sidebar().also { it.render() }
    val serviceSelector = ServiceSelector(client).also { it.render() }
    sidebar.elem.appendChild(serviceSelector.elem)
    body.appendChild(sidebar.elem)

    val content = document.create.div {
        inlineStyle {
            position = "fixed"
            top = 48.px
            left = 190.px

            height = "calc(100vh - 48px)"
            width = "calc(100vw - 190px)"

            overflow = "hidden"

            paddingTop = 16.px
            paddingLeft = 16.px
            paddingRight = 16.px

            display = "flex"
            gap = 32.px
            flexDirection = "column"
        }
    }

    val statusCards = StatusCards(client)
    content.appendChild(statusCards.render())

    val filters = Filters()
    content.appendChild(filters.render())

    val log = Log(filters)
    content.appendChild(log.render())

    body.appendChild(content)

    client.onMessage = { message ->
        when (message) {
            is ServerToClient.Log -> {
                if (message.clearExisting) {
                    log.clear()
                }
                log.addMessages(message.messages)
            }

            is ServerToClient.NewService -> {
                serviceSelector.addService(message.service)
            }

            is ServerToClient.Statistics -> {
                statusCards.updateStats(message)
            }
        }
    }

    client.onOpen = {
        statusCards.updateInterests(listOf("UCloud", "IM/Server", "IM/User"))
    }

    client.start()
}
